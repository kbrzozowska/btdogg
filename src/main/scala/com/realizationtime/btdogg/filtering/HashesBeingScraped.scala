package com.realizationtime.btdogg.filtering

import java.time.Instant
import java.time.format.DateTimeFormatter

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.BtDoggConfiguration.RedisConfig
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.filtering.FilteringProcess.Result.{ALREADY_EXISTED, NEW}
import redis.RedisClient

import scala.concurrent.ExecutionContext

class HashesBeingScraped(val hashesBeingScrapedDB: RedisClient)(implicit private val ec: ExecutionContext) {

  val flow: Flow[TKey, TKey, NotUsed] = Flow[TKey]
    .mapAsyncUnordered(RedisConfig.parallelismLevel)(key => {
      val now = Instant.now()
      hashesBeingScrapedDB.set(key.hash, DateTimeFormatter.ISO_INSTANT.format(now), NX = true)
        .map {
          case true => (NEW, key)
          case false => (ALREADY_EXISTED, key)
        }
    })
    .filter(_._1 == NEW)
    .map(_._2)

}
