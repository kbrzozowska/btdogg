package com.realizationtime.btdogg.mongo

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.commons.{FileEntry, TKey}
import com.realizationtime.btdogg.commons.FileEntry.{TorrentDir, TorrentFile}
import com.realizationtime.btdogg.commons.mongo.MongoTorrent.Liveness
import com.realizationtime.btdogg.commons.mongo.MongoTorrent
import com.realizationtime.btdogg.mongo.MongoTorrentWriter.localDateToString
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONDocumentWriter, BSONInteger, BSONString, BSONWriter, Macros}

trait MongoTorrentWriter {

  implicit val tkeyWriter = new BSONWriter[TKey, BSONString] {
    override def write(k: TKey): BSONString = BSONString(k.hash)
  }
  implicit val torrentDataFileWriter: BSONDocumentWriter[TorrentFile] = Macros.writer[TorrentFile]
  implicit val torrentDataDirWriter: BSONDocumentWriter[TorrentDir] = Macros.writer[TorrentDir]
  implicit val torrentDataWriter: BSONDocumentWriter[FileEntry] = Macros.writer[FileEntry]
  implicit val instantWriter = new BSONWriter[Instant, BSONDateTime] {
    override def write(t: Instant): BSONDateTime = BSONDateTime(t.toEpochMilli)
  }

  implicit val torrentWriter: BSONDocumentWriter[MongoTorrent] = Macros.writer[MongoTorrent]

  implicit val livenessWriter: BSONDocumentWriter[Liveness] = Macros.writer[Liveness]

  implicit val localDateWriter = new BSONWriter[LocalDate, BSONString] {
    override def write(t: LocalDate): BSONString = BSONString(localDateToString(t))
  }

  implicit val mapWriter: BSONDocumentWriter[Map[LocalDate, Int]] = new BSONDocumentWriter[Map[LocalDate, Int]] {
    def write(map: Map[LocalDate, Int]): BSONDocument = {
      val elements = map.toStream.map { tuple =>
        localDateToString(tuple._1) -> BSONInteger(tuple._2)
      }
      BSONDocument(elements)
    }
  }

}

object MongoTorrentWriter {

  def localDateToString(date: LocalDate): String = date.toString

}
