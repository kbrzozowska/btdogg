package com.realizationtime.btdogg.hashessource

import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorRef
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.{bootNodeHost, bootNodePort, storageBaseDir}
import com.realizationtime.btdogg.TKey
import lbms.plugins.mldht.DHTConfiguration
import lbms.plugins.mldht.kad.DHT.IncomingMessageListener
import lbms.plugins.mldht.kad.messages._
import lbms.plugins.mldht.kad.{DHT, Key}

class DhtWrapper(val hashesSource: ActorRef, port: Integer) {

  def stop() = {
    dht.stop()
  }

  private def sendKey(infoHash: Key): Unit = {
    hashesSource ! TKey(infoHash)
  }

  val storagePath = Paths.get(storageBaseDir, port.toString)
  Files.createDirectories(storagePath)
  val dht = new DHT(DHT.DHTtype.IPV4_DHT)

  dht.addIncomingMessageListener(new IncomingMessageListener {
    override def received(dht: DHT, msg: MessageBase): Unit = msg match {
      case msg: GetPeersRequest =>
        sendKey(msg.getInfoHash)
      case msg: AnnounceRequest =>
        sendKey(msg.getInfoHash)
    }
  })

  dht.start(new DHTConfiguration {
    override def getListeningPort: Int = port

    override def allowMultiHoming(): Boolean = false

    override def isPersistingID: Boolean = true

    override def getStoragePath: Path = storagePath

    override def noRouterBootstrap(): Boolean = false
  })
  if (bootNodeHost.isDefined)
    dht.addDHTNode(bootNodeHost.get, bootNodePort.get)
  else
    dht.bootstrap()

}