package com.realizationtime.btdogg

import akka.actor.{Actor, ActorRef}
import com.realizationtime.btdogg.HashesSource.{Start, StartingCompleted}

class HashesSource extends Actor with akka.actor.ActorLogging {

  override def receive: Receive = {
    case Start(portNumber) =>
      context.become(working(portNumber))
  }

  def working(portNumber: Int): Receive = {
    log.info(s"node $portNumber starting")
    val dht: DhtWrapper = new DhtWrapper(self, portNumber)
    val workingBehaviour: Receive = {
      case k: TKey => context.parent ! k
    }
    sender ! StartingCompleted(self)
    workingBehaviour
  }

}

object HashesSource {
  final case class Start(portNumber: Int)
  final case class StartingCompleted(node: ActorRef)
}