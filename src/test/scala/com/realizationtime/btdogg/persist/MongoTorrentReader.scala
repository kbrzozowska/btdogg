package com.realizationtime.btdogg.persist

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.realizationtime.btdogg.persist.FixLivenessDates.TorrentParsed
import com.realizationtime.btdogg.persist.MongoPersist.Liveness
import com.realizationtime.btdogg.{BtDoggConfiguration, TKey}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONNumberLike, BSONValue}

import scala.concurrent.ExecutionContext

trait MongoTorrentReader {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

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

  val mongo = MongoPersist(BtDoggConfiguration.MongoConfig.uri)

  val connection: MongoPersist.ConnectionWrapper = mongo.connection
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()


}
