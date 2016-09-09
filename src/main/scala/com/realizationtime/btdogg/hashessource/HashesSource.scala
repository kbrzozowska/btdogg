package com.realizationtime.btdogg.hashessource

import akka.actor.{Actor, ActorRef}
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.hashessource.HashesSource.{Start, StartingCompleted, Stop}

class HashesSource extends Actor with akka.actor.ActorLogging {

  override def receive: Receive = {
    case Start(portNumber) =>
      context.become(working(portNumber))
  }

  def working(portNumber: Int): Receive = {
    log.debug(s"node $portNumber starting")
    val dht: DhtWrapper = new DhtWrapper(self, portNumber)
    val workingBehaviour: Receive = {
      case k: TKey =>
        log.debug(s"node $portNumber: sending key: ${k.hash}")
        context.parent ! k
      case Stop() =>
        dht.stop()
        context.stop(self)
    }
    sender ! StartingCompleted(self)
    workingBehaviour
  }

}

object HashesSource {
  final case class Start(portNumber: Int)
  final case class StartingCompleted(node: ActorRef)
  final case class Stop()
}