package com.realizationtime.btdogg.filtering

import java.time.Instant
import java.time.format.DateTimeFormatter

import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.TKey
import redis.RedisClient

class HashesBeingScraped(val hashesBeingScrapedDB: RedisClient) {
  val flow = Flow[TKey]
    .map(key => {
      val now = Instant.now()
      hashesBeingScrapedDB.set(key.hash, DateTimeFormatter.ISO_INSTANT.format(now))
      key
    })
}
