package com.realizationtime.btdogg

import java.nio.file.{Path, Paths}

import akka.actor.ActorRef
import lbms.plugins.mldht.DHTConfiguration
import lbms.plugins.mldht.kad.DHT.IncomingMessageListener
import lbms.plugins.mldht.kad.messages._
import lbms.plugins.mldht.kad.{DHT, Key}

class DhtWrapper(val hashesSource: ActorRef) {

  def sendKey(infoHash: Key): Unit = {
    hashesSource ! TKey(infoHash)
  }

  val dht = new DHT(DHT.DHTtype.IPV4_DHT, new Key("0123456789abcdef01230123456789abcdef0123"))

  dht.addIncomingMessageListener(new IncomingMessageListener {
    override def received(dht: DHT, msg: MessageBase): Unit = msg match {
      case msg: GetPeersRequest =>
        sendKey(msg.getInfoHash)
      case msg: AnnounceRequest =>
        sendKey(msg.getInfoHash)
    }
  })

  dht.start(new DHTConfiguration {
    override def getListeningPort: Int = 24001

    override def allowMultiHoming(): Boolean = false

    override def isPersistingID: Boolean = false

    override def getStoragePath: Path = Paths.get("/tmp/mldht")

    override def noRouterBootstrap(): Boolean = false
  })

  dht.bootstrap()

}