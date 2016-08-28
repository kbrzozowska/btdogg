package com.realizationtime.btdogg

import akka.actor.{Actor, Props}
import com.realizationtime.btdogg.HashesSource.GetAllKeys
import com.realizationtime.btdogg.RootActor.GetHashesSource

class RootActor extends Actor with akka.actor.ActorLogging {

  private val hashesSource = context.actorOf(Props[HashesSource])

  override def receive = {
    case msg: GetHashesSource => sender() ! hashesSource
    case "shutdown" => {
      log.info("shutting down actor system!")
      context.system.terminate()
    }
    case msg: GetAllKeys => hashesSource forward msg
  }

}

object RootActor {
  final case class GetHashesSource()
}
