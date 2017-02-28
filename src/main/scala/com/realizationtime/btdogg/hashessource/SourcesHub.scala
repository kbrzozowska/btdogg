package com.realizationtime.btdogg.hashessource

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.hashessource.HashesSource.Subscribe
import com.realizationtime.btdogg.hashessource.SourcesHub._

class SourcesHub extends Actor with ActorLogging {

  private var subscribers = Set[ActorRef]()

  override def receive: Receive = {
    case AddWorkers(hashSources) => hashSources.foreach(_ ! Subscribe(self))
    case SubscribePublisher(s) => subscribers += s
    case k: TKey =>
      subscribers.foreach(_ forward k)
  }

}

object SourcesHub {

  final case class AddWorkers(hashSources: Set[ActorRef])

  final case class SubscribePublisher(mainConsumer: ActorRef)

}