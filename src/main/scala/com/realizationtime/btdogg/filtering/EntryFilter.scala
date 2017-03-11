package com.realizationtime.btdogg.filtering

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.BtDoggConfiguration.RedisConfig
import com.realizationtime.btdogg.TKey
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
    val res = entryFilterDB
      .getset(key.hash, 1)
      .map {
        case Some(_) => (Result.ALREADY_EXISTED, key)
        case None => (Result.NEW, key)
      }
    incrementSpottedCounter(spotted)
    res
  }

  private def incrementSpottedCounter(spotted: SpottedHash): Unit = {
    val incrKey = spotted match {
      case Requested(key) => s"${key.hash}:R"
      case Announced(key) => s"${key.hash}:A"
    }
    entryFilterDB.incr(incrKey)
      .onComplete {
        case Failure(ex) => log.error(s"Error incrementing redis counter for $incrKey", ex)
        case _ =>
      }
  }

}
