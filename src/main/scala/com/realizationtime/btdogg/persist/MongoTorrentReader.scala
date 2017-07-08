package com.realizationtime.btdogg.persist

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentDir, TorrentFile}
import com.realizationtime.btdogg.persist.MongoPersist.{Liveness, TorrentDocument}
import com.realizationtime.btdogg.persist.MongoTorrentReader.TorrentParsed
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONDocumentReader, BSONNumberLike, BSONValue}

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

  implicit val torrentReader = new BSONDocumentReader[TorrentParsed] {
    override def read(bson: BSONDocument): TorrentParsed = {
      val id = TKey(bson.getAs[String]("_id").get)
      val liveness = bson.getAs[Liveness]("liveness").get
      TorrentParsed(id, liveness)
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

  implicit object TorrentDocumentReader extends BSONDocumentReader[TorrentDocument] {
    override def read(bson: BSONDocument): TorrentDocument = {
      val data = bson.getAs[List[BSONDocument]]("data").get
        .map(bson => {
          bson.as[FileEntry]
        })
      TorrentDocument(
        title = bson.getAs[String]("title"),
        _id = TKey(bson.getAs[String]("_id").get),
        totalSize = bson.getAs[BSONNumberLike]("totalSize").map(_.toLong).get,
        data = data,
        creation = bson.getAs[BSONDateTime]("creation").map(s => Instant.ofEpochMilli(s.value)).get,
        liveness = bson.getAs[Liveness]("liveness").get
      )
    }
  }

}

object MongoTorrentReader {

  case class TorrentParsed(id: TKey, liveness: Liveness)

}