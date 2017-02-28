package com.realizationtime.btdogg.scraping

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.Path
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.util.Collections

import akka.actor.{Actor, ActorRef}
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.{torrentFetchTimeout, torrentsTmpDir}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.scraping.TorrentScraper.{Message, ScrapeRequest, ScrapeResult}
import lbms.plugins.mldht.kad.DHT
import the8472.bt.TorrentUtils
import the8472.mldht.TorrentFetcher

import scala.compat.java8.{FutureConverters, OptionConverters}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class TorrentScraper(dht: DHT) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val fetcher: TorrentFetcher = new TorrentFetcher(Collections.singleton(dht))

  private var currentlyProcessedToRecipients = Map[ScrapeRequest, List[ActorRef]]()

  override def receive: Receive = {
    case req: ScrapeRequest if currentlyProcessedToRecipients.contains(req) =>
      val previousRecipients = currentlyProcessedToRecipients(req)
      currentlyProcessedToRecipients += req -> (sender() :: previousRecipients)
    case req: ScrapeRequest =>
      currentlyProcessedToRecipients += req -> List(sender())
      val task: TorrentFetcher#FetchTask = fetcher.fetch(req.key.mldhtKey)
      val cs = task.awaitCompletion()
      val future: Future[TorrentFetcher#FetchTask] = FutureConverters.toScala(cs)
      future
        .flatMap(fetchTask => {
          OptionConverters.toScala(fetchTask.getResult)
            .map(saveTorrentToFile(_, req))
            .getOrElse(Future.successful(ScrapeResult(req, Success(None))))
        })
        .onComplete {
          case Success(result) => self ! result
          case Failure(ex) => self ! ScrapeResult(req, Failure(ex))
        }
      context.system.scheduler.scheduleOnce(torrentFetchTimeout, self, Timeout(req, task))
    case m: Message => m match {
      case Timeout(req, task) if currentlyProcessedToRecipients.contains(req) =>
        val recipients = currentlyProcessedToRecipients(req)
        currentlyProcessedToRecipients -= req
        recipients.foreach(_ ! ScrapeResult(req, Success(None)))
        try {
          task.stop()
        } catch {
          case _: Throwable =>
        }
      case ignoreAlreadyCompleted: Timeout =>
      case res: ScrapeResult if currentlyProcessedToRecipients.contains(res.request) =>
        val recipients = currentlyProcessedToRecipients(res.request)
        currentlyProcessedToRecipients -= res.request
        recipients.foreach(_ ! res)
      case ignoreAlreadyTimedOut: ScrapeResult =>
    }
  }

  def saveTorrentToFile(inputBuffer: ByteBuffer, req: ScrapeRequest): Future[ScrapeResult] = {
    val path = torrentsTmpDir.resolve(s"${req.key.hash}.torrent")
    val torrentBytes = TorrentUtils.wrapBareInfoDictionary(inputBuffer)
    try {
      val fileChannel = AsynchronousFileChannel.open(path, CREATE, TRUNCATE_EXISTING, WRITE)
      val p = Promise[ScrapeResult]()
      fileChannel.write(torrentBytes, 0L, torrentBytes, new CompletionHandler[Integer, ByteBuffer]() {

        override def completed(result: Integer, attachment: ByteBuffer): Unit = {
          if (closeSafely())
            p.success(ScrapeResult(req, Success(Some(path))))
        }

        override def failed(exc: Throwable, attachment: ByteBuffer): Unit = {
          p.success(ScrapeResult(req, Failure(exc)))
          closeSafely()
        }

        def closeSafely(): Boolean = {
          try {
            fileChannel.close()
            true
          } catch {
            case ex: Throwable =>
              p.trySuccess(ScrapeResult(req, Failure(ex)))
              false
          }
        }
      })
      p.future
    } catch {
      case ex: Throwable => Future.successful(ScrapeResult(req, Failure(ex)))
    }
  }

  case class Timeout(req: ScrapeRequest, task: TorrentFetcher#FetchTask) extends Message

}

object TorrentScraper {

  sealed abstract trait Message

  case class ScrapeRequest(key: TKey, originalRecipient: ActorRef) extends Message

  final case class ScrapeResult(request: ScrapeRequest, result: ScrapeResult#ResultValue) extends Message {
    type ResultValue = Try[Option[Path]]
  }

}
