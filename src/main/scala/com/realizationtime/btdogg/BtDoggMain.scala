package com.realizationtime.btdogg


import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.event.Logging
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import com.realizationtime.btdogg.BtDoggConfiguration.MongoConfig.parallelismLevel
import com.realizationtime.btdogg.BtDoggConfiguration.RedisConfig
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.torrentFetchTimeout
import com.realizationtime.btdogg.RootActor.{GetScrapersHub, SubscribePublisher, UnsubscribePublisher}
import com.realizationtime.btdogg.commons.ParsingResult
import com.realizationtime.btdogg.commons.mongo.MongoTorrent
import com.realizationtime.btdogg.elastic.Elastic
import com.realizationtime.btdogg.elastic.Elastic.IndexAlreadyExisted
import com.realizationtime.btdogg.filtering.CountersFlusher.Stop
import com.realizationtime.btdogg.filtering.{CountersFlusher, FilteringProcess}
import com.realizationtime.btdogg.mongo.MongoPersist
import com.realizationtime.btdogg.scraping.ScrapingProcess
import com.realizationtime.btdogg.utils.Counter
import com.realizationtime.btdogg.utils.Counter.Tick
import com.realizationtime.btdogg.utils.FileUtils.{moveFileToFaulty, removeFile}
import redis.RedisClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class BtDoggMain {

  def shutdownNow(): Unit = scheduleShutdown(0 seconds)

  def scheduleShutdown(delay: FiniteDuration): Unit = {
    log.info(s"Ordered shutdown in $delay")
    system.scheduler.scheduleOnce(delay, () => {
      scrapersHub.value match {
        case Some(Success(FetchTorrentsResult(rootActor, publisher))) =>
          log.info("Stopping system gracefully")
          rootActor ! UnsubscribePublisher(publisher, Some(Status.Success("shutdown")))
        case Some(Failure(_)) =>
          log.info("Stopping system after failure")
          haltNow()
        case None =>
          log.error("Stopping system violently")
          haltNow()
      }
    })
  }

  private def haltNow(): Unit = {
    system.terminate()
    mongoPersist.stop()
  }

  private implicit val system = ActorSystem("BtDogg")
  private implicit val log = Logging(system, classOf[BtDoggMain])
  private val decider: Supervision.Decider = { e =>
    log.error(e, "Unhandled exception in stream")
    Supervision.resume
  }
  private val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  private implicit val materializer = ActorMaterializer(materializerSettings)
  log.info("Starting btdogg...")

  private val elastic = new Elastic(BtDoggConfiguration.ElasticConfig)

  private val mongoPersist = MongoPersist(BtDoggConfiguration.MongoConfig.uri)


  private val entryFilterDB = RedisClient(db = Some(RedisConfig.entryFilterDb))
  private val hashesCurrentlyBeingScrapedDb = RedisClient(db = Some(RedisConfig.currentlyProcessedDb))

  private val filteringProcess = new FilteringProcess(
    entryFilterDB = entryFilterDB,
    hashesBeingScrapedDB = hashesCurrentlyBeingScrapedDb,
    mongoPersist = mongoPersist
  )

  import akka.pattern.ask

  private implicit val timeout: Timeout = Timeout(torrentFetchTimeout * 2)

  private val scrapersHub = hashesCurrentlyBeingScrapedDb.flushdb()
    .flatMap(_ => elastic.ensureIndexExists())
    .flatMap(res =>
      if (res == IndexAlreadyExisted)
        Future.successful((): Unit)
      else
        elastic.importEverythingIntoElasticsearch(mongoPersist.connection)
    )
    .flatMap(_ => fetchTorrents())
    .recoverWith {
      case t =>
        log.error(t, "Scraping process failed")
        Future.failed(t)
    }

  scrapersHub.foreach(_ => log.info("Scraping process launched"))


  def fetchTorrents(): Future[FetchTorrentsResult] = {
    val keysProcessing: Promise[FetchTorrentsResult] = Promise()
    val rootActor = system.actorOf(Props[RootActor], "rootActor")

    (rootActor ? GetScrapersHub).mapTo[ActorRef].onComplete {
      case Success(scrapingHub) =>
        val scrapingProcess = new ScrapingProcess(scrapingHub, hashesCurrentlyBeingScrapedDb)
        val (publisher, completeFuture) = filteringProcess.onlyNewHashes
          .via(scrapingProcess.flow)
          .mapAsyncUnordered(parallelismLevel)(res => mongoPersist.save(res).recover {
            case ex: Throwable => ParsingResult(res.key, res.path, Failure(ex))
          })
          .map(res => {
            res match {
              case ParsingResult(key, path, Failure(ex)) =>
                log.error(ex, s"error processing torrent $key in file $path")
                moveFileToFaulty(path)
              case success =>
                removeFile(success)
            }
            res
          })
          .mapAsyncUnordered(RedisConfig.parallelismLevel)(res => {
            hashesCurrentlyBeingScrapedDb.del(res.key.hash)
              .map(_ => res)
              .recover {
                case ex: Throwable =>
                  log.error(ex, s"Error deleting ${res.key} from currently processed hashes DB")
                  res
              }
          })
          .via(elastic.insertOne)
          .map((wtf: ParsingResult[MongoTorrent]) => wtf)
          .filter(_.result.isSuccess)
          .map(Counter(window = 45 seconds))
          .toMat(Sink.foreach {
            case Tick(i, rate, res) =>
              println(s"$i. $rate/s ${res.key.hash} ${res.result.get.title.getOrElse("<NoTitle>")}")
          })(Keep.both)
          .run()

        val countersFlusher = system.actorOf(Props(classOf[CountersFlusher], entryFilterDB, mongoPersist, global, materializer), "CountersFlusher")

        completeFuture.onComplete(_ => (rootActor ? RootActor.ShutdownDHTs)
          .onComplete(_ => {
            implicit val timeout = Timeout(1 hour)
            countersFlusher ? Stop
          }
            .onComplete(_ => haltNow())))

        rootActor ! SubscribePublisher(publisher)
        keysProcessing.success(FetchTorrentsResult(rootActor = rootActor, publisher = publisher))
      case Failure(ex) =>
        log.error(ex, "Error getting ScrapersHub")
        keysProcessing.failure(ex)
    }
    keysProcessing.future
  }

  final case class FetchTorrentsResult(rootActor: ActorRef, publisher: ActorRef)

}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  println("#### Press Enter to shut system down")
  scala.io.StdIn.readLine()
  main.shutdownNow()
  Await.result(main.system.whenTerminated, Duration.Inf)
}