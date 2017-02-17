package com.realizationtime.btdogg.scraping

import java.time.Instant
import java.time.format.DateTimeFormatter

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.scraping.CheckIfKnown.Result
import com.realizationtime.btdogg.scraping.CheckIfKnown.Result.Result
import com.realizationtime.btdogg.{BtDoggConfiguration, TKey}
import redis.RedisClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckIfKnown(private val checkIfKnownDB: RedisClient) {

  val flow: Flow[TKey, TKey, NotUsed] = Flow[TKey]
    .mapAsyncUnordered(BtDoggConfiguration.parallelismLevel)(isNew)
    .filter(_._1 == Result.NEW)
    .map(_._2)

  def isNew(key: TKey): Future[(Result, TKey)] = {
    val now: Instant = Instant.now()
    checkIfKnownDB
      .getset(key.hash, DateTimeFormatter.ISO_INSTANT.format(now))
      .map {
        case Some(_) => (Result.ALREADY_EXISTED, key)
        case None => (Result.NEW, key)
      }
  }

}

object CheckIfKnown {

  object Result extends Enumeration {
    type Result = Value
    val NEW, ALREADY_EXISTED = Value
  }

}
