package com.realizationtime.btdogg.elastic

import java.net.URLEncoder
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import com.realizationtime.btdogg.BtDoggConfiguration
import com.realizationtime.btdogg.BtDoggConfiguration.ElasticConfigI
import com.realizationtime.btdogg.elastic.ElasticImportEverything.ElasticTorrent
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentDir, TorrentFile}
import com.realizationtime.btdogg.persist.MongoPersist.TorrentDocument
import com.realizationtime.btdogg.persist.{MongoPersist, MongoTorrentReader}
import com.realizationtime.btdogg.utils.Counter
import com.realizationtime.btdogg.utils.Counter.Tick
import com.sksamuel.elastic4s.TcpClient
import com.sksamuel.elastic4s.bulk.{BulkCompatibleDefinition, RichBulkItemResponse}
import com.sksamuel.elastic4s.streams.{RequestBuilder, ResponseListener}
import reactivemongo.akkastream.cursorProducer

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps

class ElasticImportEverything(private val client: TcpClient,
                              private val connection: MongoPersist.ConnectionWrapper,
                              private val config: ElasticConfigI)(implicit private val ec: ExecutionContext,
                                                                  implicit private val mat: ActorMaterializer,
                                                                  implicit private val system: ActorSystem) extends MongoTorrentReader {

  import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
  import com.sksamuel.elastic4s.streams.ReactiveElastic._
  import reactivemongo.bson._

  import scala.concurrent.duration._

  private val responseListener = new ResponseListener {
    override def onAck(resp: RichBulkItemResponse): Unit = {
      //      println(s"save succeeded: $resp")
    }

    override def onFailure(resp: RichBulkItemResponse): Unit = {
      println(s"save failed: $resp")
      println(s"save failed: ${resp.failureMessage}")
    }
  }


  def importEverything(): Future[Unit] = {
    val start = Instant.now()
    connection.collection
      .map(_.find(BSONDocument.empty)
        .sort(BSONDocument("_id" -> 1))
        .cursor[TorrentDocument]().documentSource())
      .flatMap(source => {
        import com.sksamuel.elastic4s.ElasticDsl._
        implicit val torrentRequestBuilder = new RequestBuilder[ElasticTorrent] {

          def request(t: ElasticTorrent): BulkCompatibleDefinition = indexInto(config.index / config.collection).id(t.id).doc(t)
        }
        val promise = Promise[Unit]()
        val sink: Sink[ElasticTorrent, NotUsed] = Sink.fromSubscriber(
          client.subscriber[ElasticTorrent](concurrentRequests = 1,
            listener = responseListener, batchSize = 100,
            completionFn = () => {
              promise.success()
              ()
            },
            errorFn = (t: Throwable) => {
              promise.failure(t)
              ()
            }
          ))

        source
          .take(1000)
          .map(ElasticTorrent(_))
          .async
          //          .mapAsync(1)(t => {
          //          client.execute {
          //            indexInto("btdogg" / "torrent").id(t.id).doc(t)
          //          }.map(_ => t)
          //        })
          .alsoTo(Flow[ElasticTorrent].map(Counter(window = 2 minutes))
          .filter(_.i % 1000L == 0)
          //          .zipWithIndex
          //          .filter(_._2 % 1000L == 0)
          //          .toMat(Sink.foreach({ case (item, i) =>
          .to(Sink.foreach({
          case Tick(i, rate, item) =>
            val now = Instant.now()
            println(s"$i. $rate/s ${java.time.Duration.between(start, now).getSeconds} ${item.id} ${item.title}")
          //            println("Json:")
          //            val mapper = new ObjectMapper() with ScalaObjectMapper
          //            mapper.registerModule(DefaultScalaModule)
          //              .registerModule(new JavaTimeModule)
          //              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          //            println(mapper.writeValueAsString(item))
          //          //          println(s"$i. ${java.time.Duration.between(start, now).getSeconds} ${item.id} ${item.title}")
          //          //            println(s"$i. $rate/s $item")
        })))
          .runWith(sink)
        promise.future
        //          .run()
      })
  }

}

object ElasticImportEverything {

  case class ElasticTorrent(id: String,
                            title: Option[String],
                            totalSize: Long,
                            files: List[ElasticFile],
                            created: Instant,
                            liveness: Int) {

    val magnet = s"magnet:?xt=urn:btih:$id&dn=${URLEncoder.encode(title.getOrElse(""), "utf-8")}&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Fopentor.org%3A2710&tr=udp%3A%2F%2Ftracker.ccc.de%3A80&tr=udp%3A%2F%2Ftracker.blackunicorn.xyz%3A6969&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969"

  }

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