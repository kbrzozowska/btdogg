package com.realizationtime.btdogg.filtering

import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import com.realizationtime.btdogg.BtDoggConfiguration.standardBufferSize
import com.realizationtime.btdogg.TKey
import redis.RedisClient

class FilteringProcess(val checkIfKnownDB: RedisClient,
                       val hashesBeingScrapedDB: RedisClient) {

  val onlyNewHashes: Source[TKey, ActorRef] = Source.actorRef(bufferSize = standardBufferSize, OverflowStrategy.dropNew)
    .via(new CheckIfKnown(checkIfKnownDB).flow)
    .via(new HashesBeingScraped(hashesBeingScrapedDB).flow)

}
