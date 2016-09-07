package com.realizationtime.btdogg

import java.io.File
import java.nio.file.{Files, Paths}

import com.typesafe.config.{Config, ConfigFactory}


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

  object HashSourcesConfig {
    val config = rootConfig.getConfig("hashSources")
    val storageBaseDir = config.getString("storageBaseDir")
    val nodesCount = config.getInt("nodesCount")
    val firstPort = config.getInt("firstPort")
    val lastPort = config.getInt("lastPort")
    if (lastPort - firstPort < nodesCount - 1)
      throw new IllegalStateException(
        s"Not enough ports for nodes specified: there are nedded $nodesCount ports, " +
          s"but first port specified is $firstPort and the last one: $lastPort"
      )
    val nodesCreationInterval = config.getInt("nodesCreationIntervalMillis") millis

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
}
