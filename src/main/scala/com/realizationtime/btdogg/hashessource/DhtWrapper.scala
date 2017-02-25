package com.realizationtime.btdogg.hashessource

import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorRef
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.{bootNodeHost, bootNodePort, storageBaseDir}
import com.realizationtime.btdogg.TKey
import lbms.plugins.mldht.DHTConfiguration
import lbms.plugins.mldht.kad.DHT.IncomingMessageListener
import lbms.plugins.mldht.kad.messages._
import lbms.plugins.mldht.kad.{DHT, Key}

case class DhtWrapper(hashesSource: ActorRef, port: Int) {

  def stop() = {
    dht.stop()
  }

  private def sendKey(infoHash: Key): Unit = {
    hashesSource ! TKey(infoHash)
  }

  private val storagePath = Paths.get(storageBaseDir, port.toString)
  Files.createDirectories(storagePath)
  private val dht = new DHT(DHT.DHTtype.IPV4_DHT)

  dht.addIncomingMessageListener(new IncomingMessageListener {
    override def received(dht: DHT, msg: MessageBase): Unit = msg match {
      case msg: GetPeersRequest =>
        sendKey(msg.getInfoHash)
      case msg: AnnounceRequest =>
        sendKey(msg.getInfoHash)
      case _ =>
    }
  })

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

}