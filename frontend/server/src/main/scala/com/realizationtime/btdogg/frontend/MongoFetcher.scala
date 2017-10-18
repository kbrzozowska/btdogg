package com.realizationtime.btdogg.frontend

import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.commons.mongo.MongoTorrent
import com.realizationtime.btdogg.mongo.{MongoConnectionWrapper, MongoTorrentReader}

import scala.concurrent.{ExecutionContext, Future}

class MongoFetcher(uri: String)(implicit ec: ExecutionContext) extends MongoTorrentReader {

  private val connection = MongoConnectionWrapper.connect(uri)

  def fetch(hash: TKey): Future[Option[MongoTorrent]] = {
    import reactivemongo.bson._
    connection.collection
      .flatMap(_.find(BSONDocument("_id" -> hash.hash)).one[MongoTorrent])
  }

  def stop() = connection.stop()

}
