package com.realizationtime.btdogg.dhtmanager

import com.typesafe.scalalogging.Logger
import lbms.plugins.mldht.kad.DHT.LogLevel
import lbms.plugins.mldht.kad.{DHT, DHTLogger}

class BtDoggDHTLogger extends DHTLogger {
  private val log = Logger(classOf[BtDoggDHTLogger])
  private val logFatal = Logger("com.realizationtime.btdogg.dhtmanager.BtDoggDHTLoggerFatal")

  override def log(message: String, l: LogLevel): Unit = {
    val loggingFunction: (String) => Unit = l match {
      case LogLevel.Fatal => (x) => {
        logFatal.error(x)
        log.error(x)
      }
      case LogLevel.Error => (x) => log.warn(x)
      case LogLevel.Info => (x) => log.info(x)
      case LogLevel.Debug => (x) => log.debug(x)
      case LogLevel.Verbose => (x) => log.trace(x)
    }
    loggingFunction(message)
  }

  override def log(t: Throwable, l: LogLevel): Unit = {
    val loggingFunction: (String, Throwable) => Unit = l match {
      case LogLevel.Fatal => (x, t) => {
        logFatal.error(x, t)
        log.error(x, t)
      }
      case LogLevel.Error => (x, t) => log.warn(x, t)
      case LogLevel.Info => (x, t) => log.info(x, t)
      case LogLevel.Debug => (x, t) => log.debug(x, t)
      case LogLevel.Verbose => (x, t) => log.trace(x, t)
    }
    loggingFunction(t.getMessage, t)
  }

}

object BtDoggDHTLogger {
  def attach(): Unit = DHT.setLogger(new BtDoggDHTLogger)
}
