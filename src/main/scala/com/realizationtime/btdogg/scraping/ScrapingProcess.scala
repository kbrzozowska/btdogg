package com.realizationtime.btdogg.scraping

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.nodesCount
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.{simultaneousTorrentsPerNode, torrentFetchTimeout}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.{FileParser, ParsingResult}
import com.realizationtime.btdogg.scraping.ScrapersHub.ScrapeResult
import com.realizationtime.btdogg.utils.FileUtils
import com.typesafe.scalalogging.Logger
import sun.plugin.dom.exception.InvalidStateException

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class ScrapingProcess(scrapingHub: ActorRef)(private implicit val ec: ExecutionContext) {

  import akka.pattern.ask
  private implicit val timeout: Timeout = Timeout(torrentFetchTimeout * 2)
  private val log = Logger(classOf[ScrapingProcess])

  val flow: Flow[TKey, ParsingResult, NotUsed] = Flow[TKey]
    .mapAsyncUnordered(simultaneousTorrentsPerNode * nodesCount)(key =>
      (scrapingHub ? key).mapTo[ScrapeResult].recover {
        case ex: Throwable => ScrapeResult(key, Failure(ex))
      })
    .map {
      case ScrapeResult(key, Success(Some(file))) =>
        val newLocation = FileUtils.moveFileToDone(file)
        ScrapeResult(key, Success(Some(newLocation)))
      case sr: ScrapeResult => sr
    }
    .filter {
      case ScrapeResult(_, Success(Some(_))) =>
        true
      case ScrapeResult(_, Success(None)) =>
        false
      case ScrapeResult(k, Failure(ex)) =>
        log.error(s"Error fetching torrent with hash: ${k.hash}", ex)
        false
    }
    .map {
      case ScrapeResult(key, Success(Some(path))) =>
        FileParser.parse(key, path)
      case never => throw new InvalidStateException(s"this: $never should be filtered out in previous step")
    }

}
