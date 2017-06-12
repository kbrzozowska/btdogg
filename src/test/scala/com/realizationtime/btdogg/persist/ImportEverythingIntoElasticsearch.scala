package com.realizationtime.btdogg.persist

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}

import akka.stream.scaladsl.{Keep, Sink}
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentDir, TorrentFile}
import com.realizationtime.btdogg.persist.ImportEverythingIntoElasticsearch.ElasticTorrent
import com.realizationtime.btdogg.persist.MongoPersist.{Liveness, TorrentDocument}
import com.realizationtime.btdogg.utils.Counter
import com.realizationtime.btdogg.utils.Counter.Tick
import com.realizationtime.btdogg.{BtDoggConfiguration, TKey}
import com.sksamuel.elastic4s.bulk.BulkCompatibleDefinition
import com.sksamuel.elastic4s.streams.RequestBuilder
import com.sksamuel.elastic4s.{ElasticsearchClientUri, TcpClient}
import org.elasticsearch.common.settings.Settings
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import reactivemongo.akkastream.cursorProducer

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.language.postfixOps


class ImportEverythingIntoElasticsearch extends FlatSpec with Matchers with PropertyChecks with MongoTorrentReader {

  import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
  import com.sksamuel.elastic4s.streams.ReactiveElastic._
  import reactivemongo.bson._

  import scala.concurrent.duration._

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


  //"All torrents"
  ignore should "be imported into Elasticsearch" in {
    val fut = connection.collection
      .map(_.find(BSONDocument.empty)
        .sort(BSONDocument("_id" -> 1))
        .cursor[TorrentDocument]().documentSource())
      .flatMap(source => {
        val client = TcpClient.transport(Settings.builder().put("cluster.name", "docker-cluster").build(),
          ElasticsearchClientUri("127.0.0.1", 9300))
        //        client.execute
        //        {
        //          createIndex("torrents")
        //        }.flatMap(status => {
        val start = Instant.now()
        implicit val torrentRequestBuilder = new RequestBuilder[ElasticTorrent] {

          import com.sksamuel.elastic4s.ElasticDsl._

          // the request returned doesn't have to be an index - it can be anything supported by the bulk api
          def request(t: ElasticTorrent): BulkCompatibleDefinition = indexInto("torrents" / "torrents").doc(t)
        }
        source
          .async
//          .take(1000)
          .map(ElasticTorrent(_))
          .async
          //          .alsoTo(Flow[ElasticTorrent].batch(100, el => List(el))((sum, el) => el :: sum)
          //            .to(Sink.for))
          .alsoTo(Sink.fromSubscriber(client.subscriber[ElasticTorrent](concurrentRequests = 1)))
          .map(Counter(window = 2 minutes))
          .filter(_.i % 1000L == 0)
          //          .zipWithIndex
          //          .filter(_._2 % 1000L == 0)
          //          .toMat(Sink.foreach({ case (item, i) =>
          .toMat(Sink.foreach({ case Tick(i, rate, item) =>
          val now = Instant.now()
          println(s"$i. $rate/s ${java.time.Duration.between(start, now).getSeconds} ${item.id} ${item.title}")
          //          println(s"$i. ${java.time.Duration.between(start, now).getSeconds} ${item.id} ${item.title}")
          //            println(s"$i. $rate/s $item")
        }))(Keep.right)
          .run()
          .map(_ => {
            val now = Instant.now()
            println(s"${java.time.Duration.between(start, now)}")
            mongo.stop()
          })
      })
    Await.ready(fut, Duration.Inf)
    println(s"fut: $fut")
    fut.failed.foreach(_.printStackTrace())
  }

  //  "flatFiles"
  ignore should "flat" in {
    val dir = TorrentDir("dir", List(TorrentFile("file", 42L)))
    val flat = ElasticTorrent.flatFiles(List(dir)).head
    flat.name shouldBe "dir/file"
  }

}

object ImportEverythingIntoElasticsearch {

  case class ElasticTorrent(id: String,
                            title: Option[String],
                            totalSize: Long,
                            files: List[ElasticFile],
                            creation: Instant,
                            liveness: Int)

  private def addSlashIfNeeded(prefix: String) = if (prefix.isEmpty) "" else prefix + "/"

  case class ElasticFile(name: String, size: Long) {
    def this(prefix: String, torrentFile: TorrentFile) = this(addSlashIfNeeded(prefix) + torrentFile.name, torrentFile.size)
  }

  object ElasticTorrent {

    def apply(mongoTorrent: TorrentDocument): ElasticTorrent = {
      val files: List[ElasticFile] = flatFiles(mongoTorrent.data)
      val liveness: Int = flatLiveness(mongoTorrent.liveness)
      ElasticTorrent(mongoTorrent._id.hash, mongoTorrent.title, mongoTorrent.totalSize, files, mongoTorrent.creation, liveness)
    }

    def flatFiles(data: List[FileEntry]): List[ElasticFile] = {
      @tailrec
      def flatFilesRec(data: List[(String, FileEntry)], acc: List[ElasticFile]): List[ElasticFile] = data match {
        case Nil => acc.reverse
        case (prefix, file: TorrentFile) :: tail => flatFilesRec(tail, new ElasticFile(prefix, file) +: acc)
        case (prefix, dir: TorrentDir) :: tail =>
          val dirContentFlatten = dir.contents.map(file => (addSlashIfNeeded(prefix) + dir.name, file))
          flatFilesRec(dirContentFlatten ++ tail, acc)
      }

      flatFilesRec(data.map(("", _)), Nil)
    }

    val noOlderThan: LocalDate = Instant.now().minus(17, ChronoUnit.DAYS).atZone(BtDoggConfiguration.timeZone).toLocalDate

    def flatLiveness(liveness: MongoPersist.Liveness): Int = {
      val announces = liveness.announces.filterKeys(!_.isBefore(noOlderThan))
        .values.sum
      val anyRequests = liveness.requests.filterKeys(!_.isBefore(noOlderThan)).nonEmpty
      val score = announces + (if (anyRequests) 1 else 0)
      score
    }

  }

}
