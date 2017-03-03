package com.realizationtime.btdogg.hashessource

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.hashessource.HashesSource.Subscribe
import com.realizationtime.btdogg.hashessource.SourcesHub._

class SourcesHub extends Actor with ActorLogging {

  private var subscribers = Set[ActorRef]()

  override def receive: Receive = {
    case m: Message => m match {
      case AddWorkers(hashSources) => hashSources.foreach(_ ! Subscribe(self))
      case SubscribePublisher(s) => subscribers += s
      case UnsubscribePublisher(sub, msg) =>
        msg.foreach(sub ! _)
        subscribers -= sub
    }
    case k: TKey =>
      subscribers.foreach(_ forward k)
  }

}

object SourcesHub {

  sealed trait Message

  final case class AddWorkers(hashSources: Set[ActorRef]) extends Message

  final case class SubscribePublisher(mainConsumer: ActorRef) extends Message

  final case class UnsubscribePublisher(subscriber: ActorRef, endMessage: Option[Any]) extends Message

}