package com.realizationtime.btdogg


import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.nodesCount
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.{simultaneousTorrentsPerNode, torrentFetchTimeout}
import com.realizationtime.btdogg.RootActor.{GetScrapersHub, SubscribePublisher}
import com.realizationtime.btdogg.filtering.FilteringProcess
import com.realizationtime.btdogg.scraping.ScrapersHub.ScrapeResult
import redis.RedisClient

import scala.collection.immutable.Queue
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success}

class BtDoggMain {

  def shutdownNow() = scheduleShutdown(0 seconds)

  def scheduleShutdown(delay: FiniteDuration) = {
    //    system.scheduler.scheduleOnce(delay, rootActor, RootActor.Shutdown())
  }

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
  //  private implicit val timeout: Timeout = Timeout(nodesCreationInterval * nodesCount * 3)
  private implicit val log = Logging(system, classOf[BtDoggMain])
  private val decider: Supervision.Decider = { e =>
    log.error(e, "Unhandled exception in stream")
    Supervision.resume
  }
  private val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  private implicit val materializer = ActorMaterializer(materializerSettings)
  log.info("Starting btdogg...")

  val filteringProcess = new FilteringProcess(
    checkIfKnownDB = RedisClient(db = Some(1)),
    hashesBeingScrapedDB = RedisClient(db = Some(2))
  )

  val window = 3000
  private val startTime = System.nanoTime()

  private val doneTorrents = BtDoggConfiguration.ScrapingConfig.torrentsTmpDir.resolve("done")
  Files.createDirectories(doneTorrents)

  import akka.pattern.ask

  implicit val timeout: Timeout = Timeout(torrentFetchTimeout * 2)

  (rootActor ? GetScrapersHub).mapTo[ActorRef].onComplete {
    case Success(scrapingHub) =>
      val publisher = filteringProcess.onlyNewHashes
        .mapAsyncUnordered(simultaneousTorrentsPerNode * nodesCount)(key => (scrapingHub ? key).mapTo[ScrapeResult])
        .map {
          case ScrapeResult(key, Success(Some(file))) =>
            val filename = file.getFileName
            val newLocation = doneTorrents.resolve(filename)
            Files.move(file, newLocation, REPLACE_EXISTING)
            ScrapeResult(key, Success(Some(newLocation)))
          case sr: ScrapeResult => sr
        }
        .toMat(Sink.fold((0L, Queue[Long](startTime)))((u, k) => {
          val suchNow = System.nanoTime()
          val (oldI, oldHistory) = u
          val (n, time, newHistory) =
            if (oldHistory.length < window)
              (oldHistory.length + 1, oldHistory.front, oldHistory.enqueue(suchNow))
            else {
              val (time, dequeued) = oldHistory.dequeue
              (oldHistory.length, time, dequeued.enqueue(suchNow))
            }
          val i = oldI + 1
          val rate: BigDecimal = BigDecimal(n) / (BigDecimal(suchNow - time) / BigDecimal(1000000000L))
          val rateScaled = rate.setScale(3, RoundingMode.HALF_UP)
          println(s"$i. $rateScaled/s $k")
          (i, newHistory)
        }))(Keep.left)
        .run()

      rootActor ! SubscribePublisher(publisher)
    case Failure(ex) => log.error(ex, "Error getting ScrapersHub")
  }

}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  println("#### Press Enter to shut system down")
  scala.io.StdIn.readLine()
  main.shutdownNow()
  Await.result(main.system.whenTerminated, Duration.Inf)
}