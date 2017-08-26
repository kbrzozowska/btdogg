package com.realizationtime.btdogg

import java.io.{OutputStream, PrintWriter}
import java.nio.file.StandardOpenOption.{TRUNCATE_EXISTING, WRITE}
import java.nio.file.{Paths, StandardOpenOption}
import java.time
import java.time.format.DateTimeFormatter
import java.time.temporal._
import java.time.{Instant, ZoneId, ZonedDateTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.realizationtime.btdogg.Statistics.TemporalUnitFromDuration
import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.utils.RedisUtils
import org.scalatest.{FlatSpec, Ignore}
import redis.{Cursor, RedisClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.math.BigDecimal.RoundingMode

@Ignore
class Statistics extends FlatSpec {
  val window = time.Duration.ofSeconds(10)
  val statsPlik = Paths.get("/tmp/statsy.csv")
  implicit val actorSystem = ActorSystem("BtdoggTestSystem")
  implicit val materializer = ActorMaterializer()

  import ExecutionContext.Implicits.global

  val redisClient = RedisClient(db = Some(BtDoggConfiguration.RedisConfig.currentlyProcessedDb))


  "statistics" should "be generated" in {
    val startTime = Instant.now()
    val times = RedisUtils.streamAll(redisClient)
      .filter(_.key.length == TKey.VALID_HASH_LENGTH)
      .map(_.value)
      .map(Instant.parse)
      .map(roundInstant)
      .zipWithIndex
      .map {
        case (time, index) =>
          if (index % 10000L == 0)
            println(s"tera: $index")
          time
      }
    val statsF: Future[Map[ZonedDateTime, Long]] = times
        .zipWithIndex
        .map(p => {
          println(s"${p._2}. ${p._1}")
          p._1
        })
      .runWith(Sink.fold(Map[ZonedDateTime, Long]().withDefaultValue(0L)) { (map, time) =>
      val i = map(time) + 1L
      map + (time -> i)
    })
    val stats: Map[ZonedDateTime, Long] = Await.result(statsF, scala.concurrent.duration.Duration.Inf)
    val runTime = time.Duration.between(startTime, Instant.now())
    println(s"czas: $runTime")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val zapisFuture: Future[IOResult] = Source(stats.toStream
      .sortBy(_._1.toEpochSecond)
    ).map {
      case (time, i) =>
        val perSecond = BigDecimal(i) / BigDecimal(window.getSeconds) setScale(1, RoundingMode.HALF_UP)
        val output = s"${formatter.format(time)};$perSecond\n"
        print(output)
        output
    }
      .map(ByteString(_))
      .runWith(FileIO.toPath(statsPlik, Set(StandardOpenOption.CREATE, TRUNCATE_EXISTING, WRITE)))
    println(s"wynik zapisu: ${Await.result(zapisFuture, scala.concurrent.duration.Duration.Inf)}")
    actorSystem.terminate()
    plotGraph
    Await.ready(actorSystem.whenTerminated, scala.concurrent.duration.Duration.Inf)
  }

  import scala.sys.process._

  def plotGraph: Unit = {
    val endProcessPromise = Promise[Int]()
    val io = BasicIO.standard(in = (in: OutputStream) => {
      val pw = new PrintWriter(in)
      val plotInput = List(
        "set datafile separator ';'",
        "set xdata time",
        "set timefmt '%Y-%m-%d %H:%M:%S'",
        "done = 0",
        "bind all 'd' 'done = 1'",
        s"plot '$statsPlik' using 1:2 smooth csplines",
        "while(!done) {",
        "pause 1",
        "}",
        "set print '-'",
        "print 'kaka kaka'"
      )
      plotInput.foreach { line =>
        pw.println(line)
      }
      pw.flush()
      Await.ready(endProcessPromise.future, Duration.Inf)
      pw.close()
    }).withOutput(BasicIO.processFully(line => {
      if (line != null && line.contains("kaka kaka"))
        endProcessPromise.success(0)
    }))
    "gnuplot".run(io)
  }

  def roundInstant(instant: Instant): ZonedDateTime = {
    instant.atZone(ZoneId.systemDefault())
      .truncatedTo(TemporalUnitFromDuration(window))
  }

}

object Statistics {

  case class TemporalUnitFromDuration(d: time.Duration) extends TemporalUnit {
    override def addTo[R <: Temporal](temporal: R, amount: Long): R = temporal.plus(amount, this).asInstanceOf[R]

    override def isDateBased: Boolean = false

    override def isDurationEstimated: Boolean = false

    override def between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long =
      temporal1Inclusive.until(temporal2Exclusive, this)

    override def isTimeBased: Boolean = true

    override def getDuration: time.Duration = d
  }

}
