package com.realizationtime.btdogg.persist

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentData}
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult, WriteResult}

import scala.concurrent.{ExecutionContext, Future}

trait MongoPersist {

  def save(sr: ParsingResult): Future[ParsingResult]

  def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult]

  def delete(torrent: TKey): Future[WriteResult]

  def stop(): Unit
}

object MongoPersist {

  def apply(uri: String)(implicit ec: ExecutionContext): MongoPersist = new MongoPersistImpl(uri)

  case class TorrentDocument(_id: TKey,
                             title: Option[String],
                             totalSize: Long,
                             data: List[FileEntry],
                             creation: Instant = Instant.now(),
                             liveness: Liveness = Liveness()) {
    def key = _id
  }

  object TorrentDocument {
    def create(key: TKey, torrent: TorrentData): TorrentDocument = TorrentDocument(key, torrent.title, torrent.totalSize, torrent.files)
  }

  case class Liveness(requests: Map[LocalDate, Int] = Map(),
                      announces: Map[LocalDate, Int] = Map())

  case class MongoWriteException(writeResult: WriteResult) extends RuntimeException(s"Error writing to MongoDB ${writeResult.writeErrors}")

  private object MongoPersistNOP extends MongoPersist {
    override def save(sr: ParsingResult): Future[ParsingResult] = Future.successful(sr)

    override def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult] =
      Future.successful(UpdateWriteResult(true, 1, 1, Nil, Nil, None, None, None))

    override def delete(torrent: TKey): Future[WriteResult] =
      Future.successful(DefaultWriteResult(true, 1, Nil, None, None, None))

    override def stop(): Unit = {}
  }

}