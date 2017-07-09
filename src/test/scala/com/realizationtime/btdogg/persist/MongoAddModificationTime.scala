package com.realizationtime.btdogg.persist

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.persist.MongoAddModificationTime.{AlreadyExisted, ResultsFolded, TorrentWithDates, Updated}
import com.realizationtime.btdogg.utils.Counter
import com.realizationtime.btdogg.utils.Counter.Tick
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import reactivemongo.akkastream.cursorProducer
import reactivemongo.bson.{BSONDocument, BSONDocumentReader}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps


class MongoAddModificationTime extends FlatSpec with Matchers with PropertyChecks with MongoTorrentReader with MongoTorrentWriter with TestTorrentReader {

  implicit val torrentReader = new BSONDocumentReader[TorrentWithDates] {
    override def read(bson: BSONDocument): TorrentWithDates = {
      val id = TKey(bson.getAs[String]("_id").get)
      val title = bson.getAs[String]("title")
      val creation = bson.getAs[Instant]("creation").get
      val modification: Option[Instant] = bson.getAs[Instant]("modification")
      TorrentWithDates(id, title, creation, modification)
    }
  }

  "Modification time" should "be added to all torrents" in {
    connection.collection.map(col => {
      val startTime = Instant.now()
      val fut: Future[ResultsFolded] = col.find(BSONDocument.empty, BSONDocument("title" -> 1, "creation" -> 1, "modification" -> 1))
        //        .sort(BSONDocument("_id" -> 1))
        .cursor[TorrentWithDates]().documentSource()
        .map(Counter(window = 45 seconds))
        .map { case Tick(i, rate, item) =>
          if (i % 1000L == 0) {
            val now = Instant.now()
            println(s"$i. ${rate().toInt}/s ${java.time.Duration.between(startTime, now).getSeconds} ${item.creation} ${item.modification}\n${item.id.hash} ${item.title.orNull}")
          }
          item
        }
        .mapAsyncUnordered(2) {
          case TorrentWithDates(id, _, creation, modification) =>
            if (modification.isEmpty)
              connection.collection.flatMap(_.update(BSONDocument("_id" -> id.hash),
                BSONDocument("$set" -> BSONDocument("modification" -> creation))
              )).map(_ => Updated)
            else
              Future.successful(AlreadyExisted)
        }
        .runFold(ResultsFolded(0, 0))((u, el) => u.add(el))
      fut.onComplete(resultsFolded => {
        println("future completed.")
        resultsFolded.foreach(res => println(s"Count: ${res.count}"))
        println(resultsFolded)
        println(s"time taken: ${java.time.Duration.between(startTime, Instant.now())}")
        connection.stop()
        mat.shutdown()
        Await.ready(system.terminate(), Duration(10, TimeUnit.SECONDS))
      })
    })
    Await.ready(system.whenTerminated, Duration.Inf)
  }

}

object MongoAddModificationTime {

  final case class TorrentWithDates(id: TKey, title: Option[String], creation: Instant, modification: Option[Instant])

  private sealed trait UpdateResult

  private case object Updated extends UpdateResult

  private case object AlreadyExisted extends UpdateResult

  private final case class ResultsFolded(updatedCount: Int, alreadyExistedCount: Int) {
    def add(updateResult: UpdateResult): ResultsFolded = updateResult match {
      case Updated => this.copy(updatedCount = updatedCount + 1)
      case AlreadyExisted => this.copy(alreadyExistedCount = alreadyExistedCount + 1)
    }

    lazy val count: Int = updatedCount + alreadyExistedCount
  }

}
