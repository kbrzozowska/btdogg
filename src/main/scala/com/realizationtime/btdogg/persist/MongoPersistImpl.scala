package com.realizationtime.btdogg.persist

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.BtDoggConfiguration.MongoConfig
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult
import com.realizationtime.btdogg.parsing.ParsingResult.TorrentData
import com.realizationtime.btdogg.persist.MongoPersist.{ConnectionWrapper, MongoWriteException, TorrentDocument}
import com.realizationtime.btdogg.persist.MongoPersistImpl.{MongoDuplicateException, connect, isDuplicateIdError}
import com.realizationtime.btdogg.persist.MongoTorrentWriter.localDateToString
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{LastError, UpdateWriteResult, WriteError, WriteResult}
import reactivemongo.api.{MongoConnection, MongoDriver}
import reactivemongo.bson.BSONDocument

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MongoPersistImpl(val uri: String)(implicit private val ec: ExecutionContext) extends MongoPersist with MongoTorrentWriter {

  override val connection: ConnectionWrapper = connect(uri)
  private val torrents = connection.collection

  override def save(sr: ParsingResult[TorrentData]): Future[ParsingResult[TorrentDocument]] = {
    sr match {
      case ParsingResult(_, _, Failure(_)) => Future.successful(sr.copyFailed())
      case ParsingResult(key, _, Success(torrentData)) =>
        val document = TorrentDocument.create(key, torrentData)
        torrents.flatMap(coll =>
          coll.insert(document).map(writeResult => writeResult.writeErrors match {
            case head :: Nil if isDuplicateIdError(head) => ParsingResult[TorrentDocument](sr.key, sr.path, Failure(MongoDuplicateException(sr.key)))
            case Nil => sr.copyTyped(document)
            case otherErrors => ParsingResult[TorrentDocument](sr.key, sr.path, Failure(MongoWriteException(writeResult)))
          }).recoverWith {
            case lastError: LastError if isDuplicateIdError(lastError) => Future.successful(ParsingResult[TorrentDocument](sr.key, sr.path, Failure(MongoDuplicateException(sr.key))))
            case ex => Future.failed(ex)
          })
    }
  }

  override def exists(key: TKey): Future[Boolean] = {
    val selector = BSONDocument("_id" -> key.hash)
    val projection = BSONDocument("_id" -> 1)
    torrents.flatMap(_.find(selector = selector, projection = projection).cursor().headOption.map {
      case Some(_) => true
      case None => false
    })
  }

  override def incrementLiveness(key: TKey, date: LocalDate, requests: Int, announces: Int): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> key)
    val dateStr = localDateToString(date)
    val update = BSONDocument(
      "$inc" -> BSONDocument(
        s"liveness.requests.$dateStr" -> requests,
        s"liveness.announces.$dateStr" -> announces
      ),
      "$set" -> BSONDocument("modification" -> Instant.now())
    )
    torrents.flatMap(_.update(selector = selector, update = update))
  }

  override def incrementLivenessRequests(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult] =
    incrementLiveness(key, "requests", date, count)

  private def incrementLiveness(key: TKey, field: String, date: LocalDate, count: Int): Future[UpdateWriteResult] = {
    val selector = BSONDocument("_id" -> key)
    val dateStr = localDateToString(date)
    val update = BSONDocument(
      "$inc" -> BSONDocument(
        s"liveness.$field.$dateStr" -> count
      ),
      "$set" -> BSONDocument("modification" -> Instant.now())
    )
    torrents.flatMap(_.update(selector = selector, update = update))
  }

  override def incrementLivenessAnnounces(key: TKey, date: LocalDate, count: Int): Future[UpdateWriteResult] =
    incrementLiveness(key, "announces", date, count)

  override def delete(torrent: TKey): Future[WriteResult] = torrents.flatMap(_.remove(BSONDocument("_id" -> torrent)))

  override def stop(): Unit = {
    connection.stop()
  }

}

object MongoPersistImpl {

  case class MongoDuplicateException(key: TKey) extends RuntimeException(s"torrent with hash ${key.hash} already existed in Mongo")

  private def isDuplicateIdError(er: WriteError) = er.code == 11000

  private def isDuplicateIdError(er: LastError) = er.code.contains(11000)

  def connect()(implicit ec: ExecutionContext): ConnectionWrapper = connect(MongoConfig.uri)

  def connect(uri: String)(implicit ec: ExecutionContext): ConnectionWrapper = {
    val parsedUri = MongoConnection.parseURI(uri).get
    val driver: MongoDriver = new reactivemongo.api.MongoDriver
    val connection: MongoConnection = driver.connection(uri).get
    val db = connection.database(parsedUri.db.get)
    val torrents: Future[BSONCollection] = db.map(_.collection("torrents"))
    ConnectionWrapper(driver, connection, torrents)
  }

}