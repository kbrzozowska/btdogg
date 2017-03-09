package com.realizationtime.btdogg

import java.nio.file.{Files, Paths}

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

  private implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

  val standardBufferSize = rootConfig.getInt("standardBufferSize")

  object HashSourcesConfig {
    private val config = rootConfig.getConfig("hashSources")
    val storageBaseDir = config.getString("storageBaseDir")
    val nodesCount = config.getInt("nodesCount")
    val firstPort = config.getInt("firstPort")
    val lastPort = config.getInt("lastPort")
    if (lastPort - firstPort < nodesCount - 1)
      throw new IllegalStateException(
        s"Not enough ports for nodes specified: there are nedded $nodesCount ports, " +
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
    val torrentsTmpDir = Paths.get(config.getString("torrentsTmpDir"))
  }

  object RedisConfig {
    private val config = rootConfig.getConfig("redis")
    val allKnownDb = config.getInt("allKnownDb")
    val currentlyProcessedDb = config.getInt("currentlyProcessedDb")
    val testDb = config.getInt("testDb")
    val parallelismLevel = config.getInt("parallelismLevel")
  }

  object MongoConfig {
    private val config = rootConfig.getConfig("mongo")
    val uri = config.getString("uri")
    val parallelismLevel = config.getInt("parallelismLevel")
  }

}
