package com.realizationtime.btdogg.mongo

import reactivemongo.api.{MongoConnection, MongoDriver}
import reactivemongo.api.collections.bson.BSONCollection

import scala.concurrent.{ExecutionContext, Future}

final case class MongoConnectionWrapper(driver: MongoDriver, connection: MongoConnection, collection: Future[BSONCollection]) {
  def stop(): Unit = {
    connection.close()
    driver.close()
  }
}

object MongoConnectionWrapper {

  def connect(uri: String)(implicit ec: ExecutionContext): MongoConnectionWrapper = {
    val parsedUri = MongoConnection.parseURI(uri).get
    val driver: MongoDriver = new reactivemongo.api.MongoDriver
    val connection: MongoConnection = driver.connection(uri).get
    val db = connection.database(parsedUri.db.get)
    val torrents: Future[BSONCollection] = db.map(_.collection("torrents"))
    MongoConnectionWrapper(driver, connection, torrents)
  }

}