package com.realizationtime.btdogg.persist

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentData, TorrentDir, TorrentFile}
import com.realizationtime.btdogg.persist.MongoPersist.ConnectionWrapper
import reactivemongo.api.{MongoConnection, MongoDriver}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult, WriteResult}

import scala.concurrent.{ExecutionContext, Future}

trait MongoPersist {

  def save(sr: ParsingResult): Future[ParsingResult]

  def exists(key: TKey): Future[Boolean]

  def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult]

  def incrementLivenessRequests(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult]

  def incrementLivenessAnnounces(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult]

  def delete(torrent: TKey): Future[WriteResult]

  def stop(): Unit

  val connection: ConnectionWrapper
}

object MongoPersist {

  def apply(uri: String)(implicit ec: ExecutionContext): MongoPersist = new MongoPersistImpl(uri)

  case class TorrentDocument(_id: TKey,
                             title: Option[String],
                             totalSize: Long,
                             data: List[FileEntry],
                             creation: Instant = Instant.now(),
                             modification: Instant,
                             liveness: Liveness = Liveness()) {
    def key: TKey = _id
  }

  object TorrentDocument {
    def create(key: TKey, torrent: TorrentData): TorrentDocument = {
      val now = Instant.now()
      TorrentDocument(
        _id = key,
        title = torrent.title,
        totalSize = torrent.totalSize,
        data = torrent.files,
        creation = now,
        modification = now
      )
    }
  }

  case class Liveness(requests: Map[LocalDate, Int] = Map(),
                      announces: Map[LocalDate, Int] = Map())

  case class MongoWriteException(writeResult: WriteResult) extends RuntimeException(s"Error writing to MongoDB ${writeResult.writeErrors}")

  final case class ConnectionWrapper(driver: MongoDriver, connection: MongoConnection, collection: Future[BSONCollection]) {
    def stop(): Unit = {
      connection.close()
      driver.close()
    }
  }

  private object MongoPersistNOP extends MongoPersist {
    override def save(sr: ParsingResult): Future[ParsingResult] = Future.successful(sr)

    private val updateSuccessful = UpdateWriteResult(true, 1, 1, Nil, Nil, None, None, None)

    override def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult] =
      Future.successful(updateSuccessful)

    override def delete(torrent: TKey): Future[WriteResult] =
      Future.successful(DefaultWriteResult(true, 1, Nil, None, None, None))

    override def stop(): Unit = {}

    override def exists(key: TKey): Future[Boolean] = Future.successful(false)

    override def incrementLivenessRequests(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult] = Future.successful(updateSuccessful)

    override def incrementLivenessAnnounces(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult] = Future.successful(updateSuccessful)

    override val connection: ConnectionWrapper = null
  }

}