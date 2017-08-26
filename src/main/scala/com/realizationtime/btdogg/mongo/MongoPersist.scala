package com.realizationtime.btdogg.mongo

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.commons.mongo.MongoTorrent
import com.realizationtime.btdogg.commons.{ParsingResult, TKey, TorrentData}
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}

import scala.concurrent.{ExecutionContext, Future}

trait MongoPersist {

  def save(sr: ParsingResult[TorrentData]): Future[ParsingResult[MongoTorrent]]

  def exists(key: TKey): Future[Boolean]

  def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult]

  def incrementLivenessRequests(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult]

  def incrementLivenessAnnounces(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult]

  def delete(torrent: TKey): Future[WriteResult]

  def stop(): Unit

  val connection: MongoConnectionWrapper
}

object MongoPersist {

  def apply(uri: String)(implicit ec: ExecutionContext): MongoPersist = new MongoPersistImpl(uri)

  def createTorrentDocument(key: TKey, torrent: TorrentData): MongoTorrent = {
    val now = Instant.now()
    MongoTorrent(
      _id = key,
      title = torrent.title,
      totalSize = torrent.totalSize,
      data = torrent.files,
      creation = now,
      modification = now
    )
  }

  case class MongoWriteException(writeResult: WriteResult) extends RuntimeException(s"Error writing to MongoDB ${writeResult.writeErrors}")

}