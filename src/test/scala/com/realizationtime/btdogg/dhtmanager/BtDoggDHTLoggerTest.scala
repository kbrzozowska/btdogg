package com.realizationtime.btdogg.dhtmanager

import lbms.plugins.mldht.kad.DHT.LogLevel
import org.scalatest.{FlatSpec, Ignore, Matchers}

@Ignore
class BtDoggDHTLoggerTest extends FlatSpec with Matchers {
  val log = new BtDoggDHTLogger

  "BtDoggDHTLogger" should "log fatals as errors to console and file" in {
    log.log("FATALS SHOULD BE SEEN AS ERRORS IN CONSOLE AND FILE", LogLevel.Fatal)
  }

  it should "log errors or info to file only" in {
    log.log("ERRORS SHOULD PRINTED TO FILE ONLY", LogLevel.Error)
    log.log("INFOS SHOULD PRINTED TO FILE ONLY", LogLevel.Info)
  }
}
