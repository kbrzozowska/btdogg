package com.realizationtime.btdogg.persist

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.realizationtime.btdogg.BtDoggConfiguration

import scala.concurrent.ExecutionContext

trait TestTorrentReader {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val mongo = MongoPersist(BtDoggConfiguration.MongoConfig.uri)

  val connection: MongoPersist.ConnectionWrapper = mongo.connection
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

}
