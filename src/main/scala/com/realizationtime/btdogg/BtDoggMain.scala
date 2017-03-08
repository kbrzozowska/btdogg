package com.realizationtime.btdogg


import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Path}

import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.event.Logging
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.nodesCount
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.{simultaneousTorrentsPerNode, torrentFetchTimeout}
import com.realizationtime.btdogg.RootActor.{GetScrapersHub, SubscribePublisher, UnsubscribePublisher}
import com.realizationtime.btdogg.filtering.FilteringProcess
import com.realizationtime.btdogg.parsing.{FileParser, ParsingResult}
import com.realizationtime.btdogg.scraping.ScrapersHub.ScrapeResult
import com.realizationtime.btdogg.utils.Counter
import com.realizationtime.btdogg.utils.Counter.Tick
import redis.RedisClient
import sun.plugin.dom.exception.InvalidStateException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class BtDoggMain {

  def shutdownNow() = scheduleShutdown(0 seconds)

  def scheduleShutdown(delay: FiniteDuration) = {
    log.info(s"Ordered shutdown in $delay")
    system.scheduler.scheduleOnce(delay, () => {
      keysProcessing match {
        case Some(publisher) => rootActor ! UnsubscribePublisher(publisher, Some(Status.Success("shutdown")))
        case None => system.terminate()
      }
    })
  }

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
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

  private val doneTorrents = BtDoggConfiguration.ScrapingConfig.torrentsTmpDir.resolve("done")
  private val faultyTorrents = BtDoggConfiguration.ScrapingConfig.torrentsTmpDir.resolve("faulty")
  Files.createDirectories(doneTorrents)
  Files.createDirectories(faultyTorrents)

  import akka.pattern.ask

  implicit val timeout: Timeout = Timeout(torrentFetchTimeout * 2)

  var keysProcessing: Option[ActorRef] = _

  def moveFileTo(file: Path, targetDir: Path): Path = {
    val filename = file.getFileName
    val newLocation = targetDir.resolve(filename)
    Files.move(file, newLocation, REPLACE_EXISTING)
    newLocation
  }

  def moveFileToFaulty(file: Path) = moveFileTo(file, faultyTorrents)

  (rootActor ? GetScrapersHub).mapTo[ActorRef].onComplete {
    case Success(scrapingHub) =>
      val (publisher, completeFuture) = filteringProcess.onlyNewHashes
        .mapAsyncUnordered(simultaneousTorrentsPerNode * nodesCount)(key =>
          (scrapingHub ? key).mapTo[ScrapeResult].recover {
            case ex: Throwable => ScrapeResult(key, Failure(ex))
          })
        .map {
          case ScrapeResult(key, Success(Some(file))) =>
            val newLocation = moveFileTo(file, doneTorrents)
            ScrapeResult(key, Success(Some(newLocation)))
          case sr: ScrapeResult => sr
        }
        .filter {
          case ScrapeResult(_, Success(Some(_))) =>
            true
          case ScrapeResult(_, Success(None)) =>
            false
          case ScrapeResult(k, Failure(ex)) =>
            log.error(ex, s"Error fetching torrent with hash: ${k.hash}")
            false
        }
        .map {
          case ScrapeResult(key, Success(Some(path))) =>
            FileParser.parse(key, path)
          case never => throw new InvalidStateException(s"this: $never should be filtered out")
        }
        .filter {
          case ParsingResult(key, path, Failure(ex)) =>
            log.error(ex, s"error parsing torrent $key in file $path")
            moveFileToFaulty(path)
            false
          case success =>
            // removeFile(success)
            true
        }
        .map(Counter(window))
        .toMat(Sink.foreach {
          case Tick(i, rate, res) =>
            println(s"$i. $rate/s ${res.key.hash} ${res.result.get.title.getOrElse("<NoTitle>")}")
        })(Keep.both)
        .run()

      completeFuture.onComplete(_ => (rootActor ? RootActor.ShutdownDHTs).onComplete(_ => system.terminate()))

      rootActor ! SubscribePublisher(publisher)
      keysProcessing = Some(publisher)
    case Failure(ex) =>
      log.error(ex, "Error getting ScrapersHub")
      keysProcessing = None
  }

  private def removeFile(res: ParsingResult): ParsingResult = {
    try {
      Files.delete(res.path)
    } catch {
      case ex: Throwable => log.error(ex, s"error deleting torrent file ${res.path}")
    }
    res
  }
}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  println("#### Press Enter to shut system down")
  scala.io.StdIn.readLine()
  main.shutdownNow()
  Await.result(main.system.whenTerminated, Duration.Inf)
}