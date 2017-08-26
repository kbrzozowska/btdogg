package com.realizationtime.btdogg.filtering

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.BtDoggConfiguration.{MongoConfig, RedisConfig}
import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.filtering.FilteringProcess.Result.{ALREADY_EXISTED, NEW}
import com.realizationtime.btdogg.mongo.MongoPersist
import com.typesafe.scalalogging.Logger
import redis.RedisClient

import scala.concurrent.{ExecutionContext, Future}

class MongoFilter(val mongoPersist: MongoPersist, val hashesBeingScrapedDB: RedisClient)(implicit ec: ExecutionContext) {

  private val log = Logger(classOf[MongoFilter])

  val flow: Flow[TKey, TKey, NotUsed] = Flow[TKey]
    .mapAsyncUnordered(MongoConfig.parallelismLevel)(key => mongoPersist.exists(key).map {
      case true => (ALREADY_EXISTED, key)
      case false => (NEW, key)
    })
    .mapAsyncUnordered(RedisConfig.parallelismLevel) {
      case (filteringResult, key) if filteringResult == NEW => Future.successful((filteringResult, key))
      case (filteringResult, key) => hashesBeingScrapedDB.del(key.hash).map(_ => (filteringResult, key))
        .recover {
          case t: Throwable =>
            log.error(s"Error deleting $key from redis hashesBeingScrapedDB", t)
            (filteringResult, key)
        }
    }
    .filter(_._1 == NEW)
    .map(_._2)

}
