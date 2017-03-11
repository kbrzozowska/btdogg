package com.realizationtime.btdogg.hashessource

import akka.actor.{Actor, ActorRef}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.hashessource.HashesSource.{SpottedHash, Subscribe}
import lbms.plugins.mldht.kad.messages.{AnnounceRequest, GetPeersRequest, MessageBase}
import lbms.plugins.mldht.kad.{DHT, Key}

class HashesSource(val dht: DHT) extends Actor with akka.actor.ActorLogging {

  private var subscribers = Set[ActorRef]()

  dht.addIncomingMessageListener((_: DHT, msg: MessageBase) => SpottedHash(msg).foreach(sendKey))

  private def sendKey(spotted: SpottedHash): Unit = {
    subscribers.foreach(_ ! spotted)
  }

  override def receive: Receive = {
    case Subscribe(s) => subscribers += s
  }

}

object HashesSource {

  final case class Subscribe(subscriber: ActorRef)

  sealed trait SpottedHash {
    val key: TKey
  }

  object SpottedHash {
    def apply(msg: MessageBase): Option[SpottedHash] = msg match {
      case msg: GetPeersRequest =>
        Some(Requested(msg.getInfoHash))
      case msg: AnnounceRequest =>
        Some(Announced(msg.getInfoHash))
      case _ => None
    }
  }

  final case class Requested(override val key: TKey) extends SpottedHash

  object Requested {
    def apply(key: Key): Requested = Requested(TKey(key))
  }

  final case class Announced(override val key: TKey) extends SpottedHash

  object Announced {
    def apply(key: Key): Announced = Announced(TKey(key))
  }

}