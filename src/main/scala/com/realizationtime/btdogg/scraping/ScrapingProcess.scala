package com.realizationtime.btdogg.scraping

import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import com.realizationtime.btdogg.TKey
import redis.RedisClient

class ScrapingProcess(
                       val checkIfKnownDB: RedisClient,
                       val hashesBeingScrapedDB: RedisClient
                     ) {

  val onlyNewHashes: Source[TKey, ActorRef] = Source.actorRef(bufferSize = 100, OverflowStrategy.dropNew)
    .via(new CheckIfKnown(checkIfKnownDB).flow)
    .via(new HashesBeingScraped(hashesBeingScrapedDB).flow)

}
