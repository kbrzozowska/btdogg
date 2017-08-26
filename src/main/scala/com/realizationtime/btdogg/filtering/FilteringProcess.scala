package com.realizationtime.btdogg.filtering

import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import com.realizationtime.btdogg.BtDoggConfiguration.standardBufferSize
import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.hashessource.HashesSource.SpottedHash
import com.realizationtime.btdogg.mongo.MongoPersist
import redis.RedisClient

import scala.concurrent.ExecutionContext

class FilteringProcess(val entryFilterDB: RedisClient,
                       val hashesBeingScrapedDB: RedisClient,
                       val mongoPersist: MongoPersist)
                      (implicit private val ec: ExecutionContext) {

  val onlyNewHashes: Source[TKey, ActorRef] = Source.actorRef[SpottedHash](bufferSize = standardBufferSize, OverflowStrategy.dropNew)
    .via(new EntryFilter(entryFilterDB).flow)
    .via(new HashesBeingScraped(hashesBeingScrapedDB).flow)
    .via(new MongoFilter(mongoPersist, hashesBeingScrapedDB).flow)

}

object FilteringProcess {

  object Result extends Enumeration {
    type Result = Value
    val NEW, ALREADY_EXISTED = Value
  }

}