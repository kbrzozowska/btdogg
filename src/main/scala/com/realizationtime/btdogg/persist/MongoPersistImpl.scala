package com.realizationtime.btdogg.persist

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentDir, TorrentFile}
import com.realizationtime.btdogg.persist.MongoPersist.{Liveness, MongoWriteException, TorrentDocument}
import com.realizationtime.btdogg.persist.MongoPersistImpl.isDuplicateIdError
import reactivemongo.api.MongoConnection
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{LastError, UpdateWriteResult, WriteError, WriteResult}
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONDocumentWriter, BSONInteger, BSONString, BSONWriter, Macros}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MongoPersistImpl(val uri: String)(implicit private val ec: ExecutionContext) extends MongoPersist {
  private val parsedUri = MongoConnection.parseURI(uri).get
  private val driver = new reactivemongo.api.MongoDriver
  private val connection = driver.connection(uri).get
  private val db = connection.database(parsedUri.db.get)

  private val torrents: Future[BSONCollection] = db.map(_.collection("torrents"))

  private implicit val tkeyWriter = new BSONWriter[TKey, BSONString] {
    override def write(k: TKey): BSONString = BSONString(k.hash)
  }
  private implicit val torrentDataFileWriter: BSONDocumentWriter[TorrentFile] = Macros.writer[TorrentFile]
  private implicit val torrentDataDirWriter: BSONDocumentWriter[TorrentDir] = Macros.writer[TorrentDir]
  private implicit val torrentDataWriter: BSONDocumentWriter[FileEntry] = Macros.writer[FileEntry]
  private implicit val instantWriter = new BSONWriter[Instant, BSONDateTime] {
    override def write(t: Instant): BSONDateTime = BSONDateTime(t.toEpochMilli)
  }
  private implicit val livenessWriter: BSONDocumentWriter[Liveness] = Macros.writer[Liveness]

  private def localDateToString(date: LocalDate): String = date.toString

  private implicit val localDateWriter = new BSONWriter[LocalDate, BSONString] {
    override def write(t: LocalDate): BSONString = BSONString(localDateToString(t))
  }
  private implicit val mapWriter: BSONDocumentWriter[Map[LocalDate, Int]] = new BSONDocumentWriter[Map[LocalDate, Int]] {
    def write(map: Map[LocalDate, Int]): BSONDocument = {
      val elements = map.toStream.map { tuple =>
        localDateToString(tuple._1) -> BSONInteger(tuple._2)
      }
      BSONDocument(elements)
    }
  }

  private implicit val torrentWriter: BSONDocumentWriter[TorrentDocument] = Macros.writer[TorrentDocument]

  override def save(sr: ParsingResult): Future[ParsingResult] = {
    torrents.flatMap(coll => sr match {
      case ParsingResult(_, _, Failure(_)) => Future.successful(sr)
      case ParsingResult(key, _, Success(torrentData)) =>
        val document = TorrentDocument.create(key, torrentData)
        coll.insert(document).map(writeResult => writeResult.writeErrors match {
          case head :: Nil if isDuplicateIdError(head) => sr
          case Nil => sr
          case otherErrors => ParsingResult(sr.key, sr.path, Failure(MongoWriteException(writeResult)))
        })
    }).recoverWith {
      case lastError: LastError if isDuplicateIdError(lastError) => Future.successful(sr)
      case ex => Future.failed(ex)
    }
  }

  override def exists(key: TKey): Future[Boolean] = {
    val selector = BSONDocument("_id" -> key.hash)
    val projection = BSONDocument("_id" -> 1)
    torrents.flatMap(_.find(selector = selector, projection = projection).cursor().headOption.map{
      case Some(_) => true
      case None => false
    })
  }

  override def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> key)
    val dateStr = localDateToString(date)
    val update = BSONDocument("$inc" -> BSONDocument(
      s"liveness.requests.$dateStr" -> requests,
      s"liveness.announces.$dateStr" -> announces
    ))
    torrents.flatMap(_.update(selector = selector, update = update))
  }

  override def incrementLivenessRequests(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult] =
    incrementLiveness(key, "requests", date, count)

  private def incrementLiveness(key: TKey, field: String, date: LocalDate, count: Int): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> key)
    val dateStr = localDateToString(date)
    val update = BSONDocument("$inc" -> BSONDocument(
      s"liveness.$field.$dateStr" -> count
    ))
    torrents.flatMap(_.update(selector = selector, update = update))
  }

  override def incrementLivenessAnnounces(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult] =
    incrementLiveness(key, "announces", date, count)

  override def delete(torrent: TKey): Future[WriteResult] = torrents.flatMap(_.remove(BSONDocument("_id" -> torrent)))

  override def stop(): Unit = {
    connection.close()
    driver.close()
  }

}

object MongoPersistImpl {

  private def isDuplicateIdError(er: WriteError) = er.code == 11000

  private def isDuplicateIdError(er: LastError) = er.code.contains(11000)

}