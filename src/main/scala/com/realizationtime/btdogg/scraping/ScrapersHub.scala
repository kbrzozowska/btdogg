package com.realizationtime.btdogg.scraping

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.scraping.ScrapersHub.{AddScrapers, Message, ScrapeResult, StatRequest}
import com.realizationtime.btdogg.scraping.TorrentScraper.ScrapeRequest

import scala.util.Random

class ScrapersHub extends Actor with ActorLogging {

  import context._

  private var idPrefixesToScrapers: Map[String, ActorRef] = Map[String, ActorRef]()
    .withDefault(keyPrefix => {
      val ret = idPrefixesToScrapers.toStream(Random.nextInt(idPrefixesToScrapers.size))
      log.debug(s"Found random scraper with prefix ${ret._1} for key $keyPrefix")
      ret._2
    })

  private var requestsCounter = Map[ScrapeRequest, Int]()
    .withDefaultValue(0)

  private def incrementCounter(request: ScrapeRequest): Unit = {
    val incrementedCounter = requestsCounter(request) + 1
    requestsCounter += request -> incrementedCounter
  }

  private def decrementCounter(request: ScrapeRequest): Unit = {
    val decrementedValue = requestsCounter(request) - 1
    if (decrementedValue <= 0)
      requestsCounter -= request
    else
      requestsCounter += request -> decrementedValue
  }


  override def preStart(): Unit = {
    import scala.language.postfixOps
    import scala.concurrent.duration._
    context.system.scheduler.schedule(1 minute, 1 minute, self, StatRequest)
  }

  override def receive: Receive = noNodesReceivedYet(List())

  def noNodesReceivedYet(waitingRequests: List[ScrapeRequest]): Receive = {
    case key: TKey =>
      val request = ScrapeRequest(key, sender())
      become(noNodesReceivedYet(request :: waitingRequests), discardOld = true)
    case m: Message => m match {
      case AddScrapers(newScrapers) =>
        idPrefixesToScrapers ++= newScrapers
        waitingRequests.foreach(self ! _)
        become(working)
      case StatRequest => logRequestsStats()
    }
  }

  val working: Receive = {
    case request: ScrapeRequest =>
      orderScraping(request)
    case key: TKey =>
      orderScraping(ScrapeRequest(key, sender()))
    case res: TorrentScraper.ScrapeResult =>
      decrementCounter(res.request)
      ScrapeResult.sendToOriginalRecipient(res)
    case m: Message => m match {
      case AddScrapers(newScrapers) => idPrefixesToScrapers ++= newScrapers
      case StatRequest => logRequestsStats()
    }
  }

  private def orderScraping(request: ScrapeRequest) = {
    incrementCounter(request)
    idPrefixesToScrapers(request.key.prefix) ! request
  }

  private def logRequestsStats() = {
    val requestCount: Int = requestsCounter.values.sum
    val perScraper = if (idPrefixesToScrapers.isEmpty) {
      0
    } else {
      requestCount / idPrefixesToScrapers.size
    }
    log.info(s"Currently processing $requestCount scraping requests, average: $perScraper per scraper")
  }

}

object ScrapersHub {

  sealed trait Message

  final case class AddScrapers(newScrapers: Map[String, ActorRef]) extends Message

  case object StatRequest extends Message

  final case class ScrapeResult(key: TKey, resultValue: TorrentScraper.ScrapeResult#ResultValue)

  object ScrapeResult {
    def apply(nodeResult: TorrentScraper.ScrapeResult): ScrapeResult = ScrapeResult(nodeResult.request.key, nodeResult.result)

    def sendToOriginalRecipient(nodeResult: TorrentScraper.ScrapeResult): Unit = {
      nodeResult.request.originalRecipient ! ScrapeResult(nodeResult)
    }
  }

}