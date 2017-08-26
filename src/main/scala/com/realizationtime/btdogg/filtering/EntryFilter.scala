package com.realizationtime.btdogg.filtering

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.BtDoggConfiguration.RedisConfig
import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.filtering.EntryFilter.{ANNOUNCED_POSTFIX, REQUEST_POSTFIX}
import com.realizationtime.btdogg.filtering.FilteringProcess.Result
import com.realizationtime.btdogg.filtering.FilteringProcess.Result.Result
import com.realizationtime.btdogg.hashessource.HashesSource.{Announced, Requested, SpottedHash}
import com.typesafe.scalalogging.Logger
import redis.RedisClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class EntryFilter(private val entryFilterDB: RedisClient)(implicit private val ec: ExecutionContext) {

  val flow: Flow[SpottedHash, TKey, NotUsed] = Flow[SpottedHash]
    .mapAsyncUnordered(RedisConfig.parallelismLevel)(isInFilter)
    .filter(_._1 == Result.NEW)
    .map(_._2)

  private val log = Logger(classOf[EntryFilter])

  private def isInFilter(spotted: SpottedHash): Future[(Result, TKey)] = {
    val key = spotted.key
    entryFilterDB
      .getset(key.hash, 1)
      .map {
        case Some(_) => (Result.ALREADY_EXISTED, key)
        case None => (Result.NEW, key)
      }
      .flatMap(resultPair => {
        incrementSpottedCounter(spotted)
          .map(_ => resultPair)
      })
  }

  private def incrementSpottedCounter(spotted: SpottedHash): Future[SpottedHash] = {
    val incrKey = spotted match {
      case Requested(key) => s"${key.hash}$REQUEST_POSTFIX"
      case Announced(key) => s"${key.hash}$ANNOUNCED_POSTFIX"
    }
    entryFilterDB.incr(incrKey)
      .recover {
        case ex: Throwable => log.error(s"Error incrementing redis counter for $incrKey", ex)
      }.map(_ => spotted)
  }

}

object EntryFilter {

  val REQUEST_POSTFIX = ":R"
  val ANNOUNCED_POSTFIX = ":A"

}
