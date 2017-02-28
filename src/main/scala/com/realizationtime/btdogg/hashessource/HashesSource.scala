package com.realizationtime.btdogg.hashessource

import akka.actor.{Actor, ActorRef}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.hashessource.HashesSource.Subscribe
import lbms.plugins.mldht.kad.messages.{AnnounceRequest, GetPeersRequest, MessageBase}
import lbms.plugins.mldht.kad.{DHT, Key}

class HashesSource(val dht: DHT) extends Actor with akka.actor.ActorLogging {

  private var subscribers = Set[ActorRef]()

  dht.addIncomingMessageListener((_: DHT, msg: MessageBase) => msg match {
    case msg: GetPeersRequest =>
      sendKey(msg.getInfoHash)
    case msg: AnnounceRequest =>
      sendKey(msg.getInfoHash)
    case _ =>
  })

  private def sendKey(infoHash: Key): Unit = {
    subscribers.foreach(_ ! TKey(infoHash))
  }

  override def receive: Receive = {
    case Subscribe(s) => subscribers += s
  }

}

object HashesSource {

  final case class Subscribe(subscriber: ActorRef)

}