package com.realizationtime.btdogg.scraping

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.nodesCount
import com.realizationtime.btdogg.BtDoggConfiguration.RedisConfig
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.{simultaneousTorrentsPerNode, torrentFetchTimeout}
import com.realizationtime.btdogg.commons.{ParsingResult, TKey, TorrentData}
import com.realizationtime.btdogg.parsing.FileParser
import com.realizationtime.btdogg.scraping.ScrapersHub.ScrapeResult
import com.realizationtime.btdogg.utils.FileUtils
import com.typesafe.scalalogging.Logger
import redis.RedisClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ScrapingProcess(scrapingHub: ActorRef, hashesBeingScrapedDB: RedisClient)(private implicit val ec: ExecutionContext) {

  import akka.pattern.ask

  private implicit val timeout: Timeout = Timeout(torrentFetchTimeout * 2)
  private val log = Logger(classOf[ScrapingProcess])

  val flow: Flow[TKey, ParsingResult[TorrentData], NotUsed] = Flow[TKey]
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
    .mapAsyncUnordered(RedisConfig.parallelismLevel) {
      case ScrapeResult(key, Success(Some(path))) =>
        Future.successful(ScrapeResult(key, Success(Some(path))))
      case ScrapeResult(key, someFailRes) =>
        hashesBeingScrapedDB.del(key.hash).map(_ => ScrapeResult(key, someFailRes))
          .recover {
            case t: Throwable =>
              log.error(s"Error deleting $key from Redis hashesBeingScrapedDB", t)
              ScrapeResult(key, someFailRes)
          }
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
      case never => throw new IllegalStateException(s"this: $never should be filtered out in previous step")
    }

}
