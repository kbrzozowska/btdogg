package com.realizationtime.btdogg.mongo

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.commons.{FileEntry, TKey}
import com.realizationtime.btdogg.commons.FileEntry.{TorrentDir, TorrentFile}
import com.realizationtime.btdogg.commons.mongo.MongoTorrent.Liveness
import com.realizationtime.btdogg.commons.mongo.MongoTorrent
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONDocumentReader, BSONNumberLike, BSONReader, BSONValue}

trait MongoTorrentReader {

  implicit val livenessMapReader = new BSONDocumentReader[Map[LocalDate, Int]] {
    override def read(bson: BSONDocument): Map[LocalDate, Int] = {
      bson.toMap
        .map { case (k, v: BSONValue) =>
          LocalDate.parse(k) -> v.as[BSONNumberLike].toInt
        }
    }
  }

  implicit val livenessReader = new BSONDocumentReader[Liveness] {
    override def read(bson: BSONDocument): Liveness = {
      Liveness(
        livenessMapReader.read(bson.getAs[BSONDocument]("requests").get),
        livenessMapReader.read(bson.getAs[BSONDocument]("announces").get)
      )
    }
  }

  implicit object TorrentFileReader extends BSONDocumentReader[TorrentFile] {
    override def read(bson: BSONDocument): TorrentFile = TorrentFile(
      bson.getAs[String]("name").get,
      bson.getAs[BSONNumberLike]("size").map(_.toLong).get
    )
  }

  implicit object TorrentDirReader extends BSONDocumentReader[TorrentDir] {
    override def read(bson: BSONDocument): TorrentDir = TorrentDir(
      bson.getAs[String]("name").get,
      //      bson.getAs[List[FileEntry]]("contents").get
      bson.getAs[List[BSONDocument]]("contents").get.map(FileEntryReader.read(_))
    )
  }

  implicit object FileEntryReader extends BSONDocumentReader[FileEntry] {
    override def read(bson: BSONDocument): FileEntry =
      if (bson.contains("contents"))
        bson.as[TorrentDir]
      else
        bson.as[TorrentFile]
  }

  implicit object instantReader extends BSONReader[BSONDateTime, Instant] {
    override def read(bson: BSONDateTime): Instant = Instant.ofEpochMilli(bson.value)
  }

  implicit object TorrentDocumentReader extends BSONDocumentReader[MongoTorrent] {
    override def read(bson: BSONDocument): MongoTorrent = {
      val data = bson.getAs[List[BSONDocument]]("data").get
        .map(bson => {
          bson.as[FileEntry]
        })
      MongoTorrent(
        title = bson.getAs[String]("title"),
        _id = TKey(bson.getAs[String]("_id").get),
        totalSize = bson.getAs[BSONNumberLike]("totalSize").map(_.toLong).get,
        data = data,
        creation = bson.getAs[Instant]("creation").get,
        modification = bson.getAs[Instant]("modification").get,
        liveness = bson.getAs[Liveness]("liveness").get
      )
    }
  }

}
