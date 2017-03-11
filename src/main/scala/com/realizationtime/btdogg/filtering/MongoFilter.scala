package com.realizationtime.btdogg.filtering

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.realizationtime.btdogg.BtDoggConfiguration.MongoConfig
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.filtering.FilteringProcess.Result.{ALREADY_EXISTED, NEW}
import com.realizationtime.btdogg.persist.MongoPersist

import scala.concurrent.ExecutionContext

class MongoFilter(val mongoPersist: MongoPersist)(implicit ec: ExecutionContext) {

  val flow: Flow[TKey, TKey, NotUsed] = Flow[TKey]
    .mapAsyncUnordered(MongoConfig.parallelismLevel)(key => mongoPersist.exists(key).map {
      case true => (ALREADY_EXISTED, key)
      case false => (NEW, key)
    })
    .filter(_._1 == NEW)
    .map(_._2)

}
