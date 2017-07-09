package com.realizationtime.btdogg

import java.nio.file.{Files, Path, Paths}
import java.time.ZoneId

import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.typesafe.config.{Config, ConfigFactory}

import scala.language.postfixOps


object BtDoggConfiguration {

  import scala.concurrent.duration._

  val CustomConfigPath = Paths.get(System.getProperty("user.dir"), "application.conf")

  private val rootConfig: Config =
    (if (Files.exists(CustomConfigPath)) {
      ConfigFactory.parseFile(CustomConfigPath.toFile)
        .withFallback(ConfigFactory.load())
    } else
      ConfigFactory.load()
    ).getConfig("btdogg")

  import com.realizationtime.btdogg.utils.TimeUtils.asFiniteDuration

  val standardBufferSize: Int = rootConfig.getInt("standardBufferSize")
  val timeZone: ZoneId = ZoneId.of(rootConfig.getString("timeZone"))

  object HashSourcesConfig {
    private val config = rootConfig.getConfig("hashSources")
    val storageBaseDir: String = config.getString("storageBaseDir")
    val nodesCount: Int = config.getInt("nodesCount")
    val prefixStep: Int = config.getInt("prefixStep")
    val firstPort: Int = config.getInt("firstPort")
    val lastPort: Int = config.getInt("lastPort")
    if (lastPort - firstPort < nodesCount - 1)
      throw new IllegalStateException(
        s"Not enough ports for nodes specified: there are needed $nodesCount ports, " +
          s"but first port specified is $firstPort and the last one: $lastPort"
      )
    val nodesCreationInterval: FiniteDuration = config.getDuration("nodesCreationInterval")

    val bootNodeHost: Option[String] =
      if (config.hasPath("bootNodeHost"))
        Option(config.getString("bootNodeHost"))
      else
        Option.empty
    val bootNodePort: Option[Int] =
      if (config.hasPath("bootNodePort"))
        Option(config.getInt("bootNodePort"))
      else
        Option.empty
  }

  object ScrapingConfig {
    private val config = rootConfig.getConfig("scraping")
    val simultaneousTorrentsPerNode: Int = config.getInt("simultaneousTorrentsPerNode")
    val torrentFetchTimeout: FiniteDuration = config.getDuration("torrentFetchTimeout")
    val torrentsTmpDir: Path = Paths.get(config.getString("torrentsTmpDir"))
  }

  object RedisConfig {
    private val config = rootConfig.getConfig("redis")
    val entryFilterDb: Int = config.getInt("entryFilterDb")
    val currentlyProcessedDb: Int = config.getInt("currentlyProcessedDb")
    val testDb: Int = config.getInt("testDb")
    val parallelismLevel: Int = config.getInt("parallelismLevel")
  }

  object MongoConfig {
    private val config = rootConfig.getConfig("mongo")
    val uri: String = config.getString("uri")
    val parallelismLevel: Int = config.getInt("parallelismLevel")
  }

  trait ElasticConfigI {

    val uri: ElasticsearchClientUri
    val clusterName: String
    val index: String
    val collection: String
    val insertBatchSize: Int
    val insertBatchParallelism: Int

  }

  object ElasticConfig extends ElasticConfigI {
    private val config = rootConfig.getConfig("elastic")
    override val uri = ElasticsearchClientUri(config.getString("host"), config.getInt("port"))
    override val clusterName: String = config.getString("clusterName")
    override val index: String = config.getString("index")
    override val collection: String = config.getString("collection")
    override val insertBatchSize: Int = config.getInt("insertBatchSize")
    override val insertBatchParallelism: Int = config.getInt("insertBatchParallelism")
  }

}
