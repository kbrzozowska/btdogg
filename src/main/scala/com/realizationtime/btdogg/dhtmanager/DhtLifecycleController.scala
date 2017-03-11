package com.realizationtime.btdogg.dhtmanager

import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.{bootNodeHost, bootNodePort, storageBaseDir}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.dhtmanager.DhtLifecycleController.{NodeStopped, StopNode}
import com.realizationtime.btdogg.dhtmanager.DhtsManager.NodeReady
import com.realizationtime.btdogg.hashessource.HashesSource
import com.realizationtime.btdogg.scraping.TorrentScraper
import lbms.plugins.mldht.DHTConfiguration
import lbms.plugins.mldht.kad.DHT

case class DhtLifecycleController(port: Int, idPrefix: Int) extends Actor with ActorLogging {

  import context._

  val key: TKey = TKey.fromPrefix(idPrefix)
  val dht: DHT = new DHT(DHT.DHTtype.IPV4_DHT, key.mldhtKey)

  private var hashesSourceWrapper: ActorRef = _
  private var scraperWrapper: ActorRef = _
  private val storagePath = Paths.get(storageBaseDir, port.toString)
  Files.createDirectories(storagePath)

  override def preStart(): Unit = {
    log.info(s"node $port starting")

    dht.start(new DHTConfiguration {
      override def getListeningPort: Int = port

      override def allowMultiHoming(): Boolean = false

      override def isPersistingID: Boolean = true

      override def getStoragePath: Path = storagePath

      override def noRouterBootstrap(): Boolean = false

      override def getUnresolvedBootstrapNodes: Array[InetSocketAddress] =
        if (bootNodeHost.isDefined)
          Array(InetSocketAddress.createUnresolved(bootNodeHost.get, bootNodePort.get))
        else
          super.getUnresolvedBootstrapNodes
    })

    if (bootNodeHost.isDefined)
      dht.addDHTNode(bootNodeHost.get, bootNodePort.get)
    dht.bootstrap()
    hashesSourceWrapper = system.actorOf(Props(classOf[HashesSource], dht), s"HashesSource$port")
    scraperWrapper = system.actorOf(Props(classOf[TorrentScraper], dht), s"TorrentScraper$port")
    val key = TKey(dht.getOurID)
    parent ! NodeReady(key, self, hashesSourceWrapper, scraperWrapper)
  }

  override def receive: Receive = {
    case StopNode =>
      log.info(s"Stopping node ${key.hash}, port: $port")
      scraperWrapper ! TorrentScraper.Shutdown
      try {
        dht.stop()
        log.info(s"Node ${key.hash}, port: $port stopped")
      } catch {
        case ex: Throwable => log.error(ex, s"Error stopping down DHT $key")
      }
      sender() ! NodeStopped
      stop(self)
  }
}

object DhtLifecycleController {
  def create(port: Int, idPrefix: Int) /*(implicit system: ActorSystem)*/ : Props =
    Props(classOf[DhtLifecycleController], port, idPrefix)

  case object StopNode

  case object NodeStopped

}
