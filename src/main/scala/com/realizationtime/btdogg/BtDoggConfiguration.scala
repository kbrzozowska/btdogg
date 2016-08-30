package com.realizationtime.btdogg

object BtDoggConfiguration {
  import scala.concurrent.duration._

  val storageBaseDir = "/tmp/mldht"
  val nodesCount= 24
  val firstPort = 24000
  val lastPort = 24999
  val nodesCreationInterval = 300 millis
}
