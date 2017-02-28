package com.realizationtime.btdogg.scraping

import akka.actor.{Actor, ActorRef}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.scraping.ScrapersHub.{AddScrapers, Message, ScrapeResult}
import com.realizationtime.btdogg.scraping.TorrentScraper.ScrapeRequest

class ScrapersHub extends Actor {

  private var idPrefixesToScrapers: Map[String, ActorRef] = Map[String, ActorRef]()
    .withDefault(_ => {
      idPrefixesToScrapers.head._2
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

  override def receive: Receive = noNodesReceivedYet(List())

  def noNodesReceivedYet(waitingRequests: List[ScrapeRequest]): Receive = {
    case key: TKey =>
      val request = ScrapeRequest(key, sender())
      context.become(noNodesReceivedYet(request :: waitingRequests), discardOld = true)
    case m: Message => m match {
      case AddScrapers(newScrapers) =>
        idPrefixesToScrapers ++= newScrapers
        waitingRequests.foreach(self ! _)
        context.become(working)
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
    }
  }

  private def orderScraping(request: ScrapeRequest) = {
    incrementCounter(request)
    idPrefixesToScrapers(request.key.prefix) ! request
  }
}

object ScrapersHub {

  sealed trait Message

  final case class AddScrapers(newScrapers: Map[String, ActorRef]) extends Message

  final case class ScrapeResult(key: TKey, resultValue: TorrentScraper.ScrapeResult#ResultValue)

  object ScrapeResult {
    def apply(nodeResult: TorrentScraper.ScrapeResult): ScrapeResult = ScrapeResult(nodeResult.request.key, nodeResult.result)

    def sendToOriginalRecipient(nodeResult: TorrentScraper.ScrapeResult): Unit = {
      nodeResult.request.originalRecipient ! ScrapeResult(nodeResult)
    }
  }

}