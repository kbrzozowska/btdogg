package com.realizationtime.btdogg.persist

import java.time.Instant

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.realizationtime.btdogg.BtDoggConfiguration
import com.realizationtime.btdogg.elastic.Elastic
import com.realizationtime.btdogg.elastic.ElasticImportEverything.ElasticTorrent
import com.realizationtime.btdogg.parsing.ParsingResult.{TorrentDir, TorrentFile}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, Ignore, Matchers}

import scala.concurrent.Await
import scala.language.postfixOps


@Ignore
class ElasticImportEverythingTest extends FlatSpec with Matchers with PropertyChecks with MongoTorrentReader with TestTorrentReader {


  import scala.concurrent.duration._


  "All torrents" should "be imported into Elasticsearch" in {
    com.sksamuel.elastic4s.jackson.JacksonSupport.mapper.registerModule(new JavaTimeModule)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    val start = Instant.now()

    val fut = new Elastic(BtDoggConfiguration.ElasticConfig).importEverythingIntoElasticsearch(connection)

    Await.ready(fut, Duration.Inf)
    println(s"fut: $fut")
    val now = Instant.now()
    println(s"${java.time.Duration.between(start, now)}")
    mongo.stop()
    fut.failed.foreach(_.printStackTrace())
  }

  //  "FlatFiles"
  ignore should "flat tree hierarchy of files into flat list" in {
    val dir = TorrentDir("dir", List(TorrentFile("file", 42L)))
    val flat = ElasticTorrent.flatFiles(List(dir)).head
    flat.name shouldBe "dir/file"
  }

}
