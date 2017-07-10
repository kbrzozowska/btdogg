package com.realizationtime.btdogg.elastic

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.realizationtime.btdogg.BtDoggConfiguration.ElasticConfigI
import com.realizationtime.btdogg.elastic.Elastic.{IndexAlreadyExisted, IndexCreated, IndexCreationResult}
import com.realizationtime.btdogg.elastic.ElasticImportEverything.ElasticTorrent
import com.realizationtime.btdogg.parsing.ParsingResult
import com.realizationtime.btdogg.persist.MongoPersist
import com.realizationtime.btdogg.persist.MongoPersist.TorrentDocument
import com.sksamuel.elastic4s.TcpClient
import com.typesafe.scalalogging.Logger
import org.elasticsearch.common.settings.Settings

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Elastic(config: ElasticConfigI)(implicit private val ec: ExecutionContext) {

  private val log = Logger(classOf[Elastic])

  private val client = TcpClient.transport(Settings.builder().put("cluster.name", config.clusterName).build(),
    config.uri)

  com.sksamuel.elastic4s.jackson.JacksonSupport.mapper.registerModule(new JavaTimeModule)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  def ensureIndexExists(): Future[IndexCreationResult] = {
    import com.sksamuel.elastic4s.ElasticDsl._
    client.execute {
      index exists config.index
    }.flatMap(indexExisted => {
      if (indexExisted.isExists)
        Future.successful(IndexAlreadyExisted)
      else {
        log.info(s"Creating index ${config.index} / ${config.collection}")
        client.execute {
          createIndex(config.index).mappings(
            mapping(config.collection) as(
              dateField("created"),
              dateField("modified")
            )
          )
        }.map(_ => IndexCreated)
      }
    })
  }

  def importEverythingIntoElasticsearch(mongoConnection: MongoPersist.ConnectionWrapper)
                                       (implicit mat: ActorMaterializer, system: ActorSystem): Future[Done] = {
    new ElasticImportEverything(client, mongoConnection, config).importEverything()
  }

  lazy val insertOne: Flow[ParsingResult[TorrentDocument], ParsingResult[TorrentDocument], NotUsed] =
    Flow[ParsingResult[TorrentDocument]].mapAsyncUnordered(config.singleInsertParallelism) {
      case res@ParsingResult(key, path, Success(torrentDocument)) =>
        import com.sksamuel.elastic4s.ElasticDsl._
        import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
        val t = ElasticTorrent(torrentDocument)
        client.execute {
          indexInto(config.index / config.collection).id(t.id).doc(t)
        }.map(_ => res)
          .recover { case thr =>
            log.error(s"Error saving torrent $t to Elasticsearch", thr)
            ParsingResult[TorrentDocument](key, path, Failure(thr))
          }
      case failed => Future.successful(failed)
    }

}

object Elastic {

  sealed trait IndexCreationResult

  object IndexCreated extends IndexCreationResult

  object IndexAlreadyExisted extends IndexCreationResult

}