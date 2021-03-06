package com.realizationtime.btdogg.mongo

import java.time.{Instant, LocalDate}
import java.util.concurrent.TimeUnit

import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.commons.mongo.MongoTorrent.Liveness
import com.realizationtime.btdogg.filtering.CountersFlusher
import com.realizationtime.btdogg.mongo.FixLivenessDates.{TorrentParsed, normalizeLiveness}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, Ignore, Matchers}
import reactivemongo.akkastream.cursorProducer
import reactivemongo.bson.{BSONDocument, BSONDocumentReader}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@Ignore
class FixLivenessDates extends FlatSpec with Matchers with PropertyChecks with MongoTorrentReader with MongoTorrentWriter with TestTorrentReader {

  implicit val torrentReader = new BSONDocumentReader[TorrentParsed] {
    override def read(bson: BSONDocument): TorrentParsed = {
      val id = TKey(bson.getAs[String]("_id").get)
      val liveness = bson.getAs[Liveness]("liveness").get
      TorrentParsed(id, liveness)
    }
  }

  "Incorrect liveness" should "get printed" in {
    connection.collection.map(col => {
      val startTime = Instant.now()
      val fut: Future[Int] = col.find(BSONDocument.empty, BSONDocument("liveness" -> 1, "creation" -> 1))
        .sort(BSONDocument("_id" -> 1))
        .cursor[TorrentParsed]().documentSource()
        .map(t => t -> normalizeLiveness(t.liveness))
        .filter {
          case (t, normL) => t.liveness != normL
        }
        //        .take(1)
        .zipWithIndex
        .map(el => {
          if (el._2 % 1000 == 0)
            println(s"${el._2} ${el._1}")
          el
        })
        .map(_._1)
        .mapAsync(1) {
          case (TorrentParsed(id, _), normalizedLiveness) =>
            connection.collection.flatMap(_.update(BSONDocument("_id" -> id.hash),
              BSONDocument("$set" -> BSONDocument("liveness" -> normalizedLiveness))
            ))
        }
        .runFold(0)((u, _) => u + 1)
      fut.onComplete(count => {
        println(s"future completed. Count: $count")
        println(s"time taken: ${java.time.Duration.between(startTime, Instant.now())}")
        connection.stop()
        mat.shutdown()
        Await.ready(system.terminate(), Duration(10, TimeUnit.SECONDS))
      })
    })
    Await.ready(system.whenTerminated, Duration.Inf)
  }

  "normalizeLiveness" should "normalize liveness" in {
    forAll(Table(
      ("inputLiveness", "normalizedLiveness"),
      (Liveness(Map(), Map()), Liveness(Map(), Map())),
      (Liveness(
        requests = Map(
          LocalDate.parse("2017-03-20") -> 1,
          LocalDate.parse("2017-03-17") -> 2,
          LocalDate.parse("2017-03-14") -> 4)),
        Liveness(requests = Map(
          LocalDate.parse("2017-03-20") -> 1,
          LocalDate.parse("2017-03-13") -> 6
        ), announces = Map())),
      (Liveness(
        announces = Map(
          LocalDate.parse("2017-03-20") -> 1,
          LocalDate.parse("2017-03-17") -> 2,
          LocalDate.parse("2017-03-14") -> 4)),
        Liveness(announces = Map(
          LocalDate.parse("2017-03-20") -> 1,
          LocalDate.parse("2017-03-13") -> 6
        ), requests = Map()))
    )) { (input, normalized) =>
      val result = normalizeLiveness(input)
      result should equal(normalized)
    }

  }

}

object FixLivenessDates {

  case class TorrentParsed(id: TKey, liveness: Liveness)

  def normalizeLiveness(l: Liveness): Liveness = {
    def normalizeMap(counters: Map[LocalDate, Int]): Map[LocalDate, Int] =
      counters.foldLeft(Map[LocalDate, Int]().withDefaultValue(0)) { case (sum, (date, count)) =>
        val startOfWeek = CountersFlusher.startOfWeek(date)
        val newCount = sum(startOfWeek) + count
        sum + (startOfWeek -> newCount)
      }

    Liveness(normalizeMap(l.requests), normalizeMap(l.announces))
  }

}
