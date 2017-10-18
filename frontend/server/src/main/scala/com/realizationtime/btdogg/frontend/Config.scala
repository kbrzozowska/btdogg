package com.realizationtime.btdogg.frontend

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ArbitraryTypeReader

trait Config {
  import Ficus._
  import ArbitraryTypeReader._

  protected case class HttpConfig(interface: String, port: Int)
  protected case class MongoConfig(uri: String)

  private val config = ConfigFactory.load()
  protected val httpConfig: HttpConfig = config.as[HttpConfig]("http")
  protected val mongoConfig: MongoConfig = config.as[MongoConfig]("mongo")
}
