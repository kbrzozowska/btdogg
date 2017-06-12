package com.realizationtime.btdogg.filtering

import java.time.temporal.ChronoField.DAY_OF_WEEK
import java.time.temporal.{ChronoField, ChronoUnit}
import java.time.{Clock, Instant, LocalDate, ZoneId}

import akka.actor.{Actor, ActorLogging, Cancellable}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.realizationtime.btdogg.BtDoggConfiguration.MongoConfig
import com.realizationtime.btdogg.BtDoggConfiguration.RedisConfig.parallelismLevel
import com.realizationtime.btdogg.{BtDoggConfiguration, TKey}
import com.realizationtime.btdogg.filtering.CountersFlusher._
import com.realizationtime.btdogg.filtering.EntryFilter.{ANNOUNCED_POSTFIX, REQUEST_POSTFIX}
import com.realizationtime.btdogg.persist.MongoPersist
import com.realizationtime.btdogg.utils.RedisUtils
import com.realizationtime.btdogg.utils.RedisUtils.KV
import redis.RedisClient

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

class CountersFlusher(private val entryFilterDB: RedisClient,
                      private val mongoPersist: MongoPersist,
                      private val clock: Clock)
                     (private implicit val ec: ExecutionContext,
                      private implicit val mat: Materializer) extends Actor with ActorLogging {
  def this(entryFilterDB: RedisClient, mongoPersist: MongoPersist)
          (implicit ec: ExecutionContext, mat: Materializer) = this(entryFilterDB, mongoPersist, Clock.systemUTC())

  import com.realizationtime.btdogg.utils.TimeUtils.asFiniteDuration

  import scala.concurrent.duration._

  private var countSchedule: Option[Cancellable] = None

  override def preStart(): Unit = {
    val now = Instant.now(clock)
    val nextRoundHour = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)
    val timeToNextRoundHour = java.time.Duration.between(now, nextRoundHour)
    countSchedule = Some(
      context.system.scheduler.schedule(timeToNextRoundHour, 1 hour, self, CountNow)
    )
  }

  override def receive: Receive = {
    case msg: Message => msg match {
      case Stop =>
        log.info("Initiating stop sequence")
        countSchedule.foreach(_.cancel())
        countSchedule = None
        val theSender = sender()
        saveCounters()
          .recover {
            case ex =>
              log.error(ex, "Final counters collection failed")
              -1L
          }
          .onComplete(_ => {
            theSender ! Stopped
            log.info("Stopped")
            context.stop(self)
          })
        context.become(stopped)
      case CountNow =>
        try {
          Await.result(saveCounters(), 1 hour)
        } catch {
          case ex: Throwable => log.error(ex, "Error collecting EntryFilter counters")
        }
    }
  }

  private val stopped: Receive = Actor.ignoringBehavior

  private def saveCounters(): Future[Long] = {
    log.info("Starting collecting of EntryFilter counters")
    val start = Instant.now(clock)
    val utcDate = start.atZone(BtDoggConfiguration.timeZone).toLocalDate
    val startOfWeek = CountersFlusher.startOfWeek(utcDate)
    RedisUtils.streamAll(entryFilterDB)
      .grouped(10)
      .mapAsyncUnordered(parallelismLevel)(kvs => {
        val keys = kvs.map(_.key)
        entryFilterDB.del(keys: _*).map(_ => kvs)
      })
      .mapConcat(kvs => kvs)
      .map(CounterEntry(_))
      .mapAsyncUnordered(MongoConfig.parallelismLevel)(entry => {
        val f: Future[Any] = entry match {
          case CounterEntry.Announced(key, count) =>
            mongoPersist.incrementLivenessAnnounces(key, startOfWeek, count)
          case CounterEntry.Requested(key, count) =>
            mongoPersist.incrementLivenessRequests(key, startOfWeek, count)
          case CounterEntry.Ignored => Future.unit
        }
        f.map(_ => entry)
      })
      .filter(_ == CounterEntry.Ignored)
      .runWith(Sink.fold(0L)((sum, _) => sum + 1))
      .map(itemsCount => {
        val time = java.time.Duration.between(start, Instant.now(clock))
        log.info(s"Done collecting EntryFilter counters. Items processed: $itemsCount, time: $time")
        itemsCount
      })
  }

}

object CountersFlusher {

  sealed trait Message

  case object CountNow extends Message

  case object Stop extends Message

  case object Stopped

  private sealed trait CounterEntry

  private object CounterEntry {
    def apply(kv: KV): CounterEntry = {
      val tkey = TKey(kv.key.take(TKey.VALID_HASH_LENGTH))
      val postfix = kv.key.drop(TKey.VALID_HASH_LENGTH)
      postfix match {
        case "" => Ignored
        case REQUEST_POSTFIX => Requested(tkey, kv.value.toInt)
        case ANNOUNCED_POSTFIX => Announced(tkey, kv.value.toInt)
      }
    }

    final case class Announced(key: TKey, count: Int) extends CounterEntry

    final case class Requested(key: TKey, count: Int) extends CounterEntry

    case object Ignored extends CounterEntry

  }

  def startOfWeek(date: LocalDate): LocalDate = {
    date.`with`(DAY_OF_WEEK, 1)
  }

}