package com.realizationtime.btdogg.dhtmanager

import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.{bootNodeHost, bootNodePort, storageBaseDir}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.dhtmanager.DhtLifecycleController.StopNode
import com.realizationtime.btdogg.dhtmanager.DhtsManager.NodeReady
import com.realizationtime.btdogg.hashessource.HashesSource
import com.realizationtime.btdogg.scraping.TorrentScraper
import lbms.plugins.mldht.DHTConfiguration
import lbms.plugins.mldht.kad.DHT

case class DhtLifecycleController(port: Int, idPrefix: Int) extends Actor with ActorLogging {

  val dht: DHT = new DHT(DHT.DHTtype.IPV4_DHT, TKey.fromPrefix(idPrefix).mldhtKey)

  private var hashesSourceWrapper: ActorRef = _
  private var scraperWrapper: ActorRef = _
  private val storagePath = Paths.get(storageBaseDir, port.toString)
  Files.createDirectories(storagePath)

  //  @scala.throws[Exception](classOf[Exception])
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
    hashesSourceWrapper = context.system.actorOf(Props(classOf[HashesSource], dht), s"HashesSource$port")
    scraperWrapper = context.system.actorOf(Props(classOf[TorrentScraper], dht), s"TorrentScraper$port")
    val key = TKey(dht.getOurID)
    context.parent ! NodeReady(key, self, hashesSourceWrapper, scraperWrapper)
  }

  override def receive: Receive = {
    case StopNode =>
      dht.stop()
      //TODO: stop wrappers
  }
}

object DhtLifecycleController {
  def create(port: Int, idPrefix: Int) /*(implicit system: ActorSystem)*/ : Props =
    Props(classOf[DhtLifecycleController], port, idPrefix)
  case object StopNode
}
