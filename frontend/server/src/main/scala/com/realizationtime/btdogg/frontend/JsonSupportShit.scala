package com.realizationtime.btdogg.frontend

import java.time.{Instant, LocalDate}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.realizationtime.btdogg.commons.FileEntry.{TorrentDir, TorrentFile}
import com.realizationtime.btdogg.commons.mongo.MongoTorrent
import com.realizationtime.btdogg.commons.mongo.MongoTorrent.Liveness
import com.realizationtime.btdogg.commons.{FileEntry, TKey}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

trait JsonSupportShit extends SprayJsonSupport {

  import DefaultJsonProtocol._ // import the default encoders for primitive types (Int, String, Lists etc)

  protected implicit val tKeyJsonFormat: RootJsonFormat[TKey] = new RootJsonFormat[TKey] {
    override def read(json: JsValue): TKey = ???

    override def write(obj: TKey): JsValue = JsString(obj.hash)
  }
  protected implicit val torrentFileJsonFormat: RootJsonFormat[TorrentFile] = jsonFormat2(TorrentFile.apply)
  protected implicit val fileEntryJsonFormat: RootJsonFormat[FileEntry] = new RootJsonFormat[FileEntry] {
    def write(obj: FileEntry) = obj match {
      case x: TorrentFile => torrentFileJsonFormat.write(x)
      case x: TorrentDir => torrentDirJsonFormat.write(x)
    }

    def read(json: JsValue) = ???
  }
  protected implicit val torrentDirJsonFormat: RootJsonFormat[TorrentDir] = jsonFormat2(TorrentDir.apply)
  protected implicit val instantJsonFormat: RootJsonFormat[Instant] = new RootJsonFormat[Instant] {
    override def read(json: JsValue) = ???

    override def write(obj: Instant) = JsString(obj.toString)
  }
  protected implicit val localDateJsonFormat: RootJsonFormat[LocalDate] = new RootJsonFormat[LocalDate] {
    override def read(json: JsValue) = ???

    override def write(obj: LocalDate) = JsString(obj.toString)
  }
  protected implicit val livenessJsonFormat: RootJsonFormat[Liveness] = jsonFormat2(Liveness)
  protected implicit val mongoTorrentJsonFormat: RootJsonFormat[MongoTorrent] = jsonFormat7(MongoTorrent.apply)

}
