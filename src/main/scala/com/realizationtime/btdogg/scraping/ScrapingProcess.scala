package com.realizationtime.btdogg.scraping

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import redis.RedisClient

class ScrapingProcess(
                       val checkIfKnownDB: RedisClient,
                       val hashesBeingScrapedDB: RedisClient
                     ) {

  val onlyNewHashes = Source.actorRef(bufferSize = 100, OverflowStrategy.dropNew)
    .via(new CheckIfKnown(checkIfKnownDB).flow)
    .via(new HashesBeingScraped(hashesBeingScrapedDB).flow)

}
