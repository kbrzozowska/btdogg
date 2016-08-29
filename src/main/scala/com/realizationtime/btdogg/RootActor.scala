package com.realizationtime.btdogg

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.AskableActorRef
import akka.stream.OverflowStrategy
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.realizationtime.btdogg.HashesSource.GetAllKeys
import com.realizationtime.btdogg.RootActor.{Boot, GetHashesSources, Shutdown}

class RootActor extends Actor with akka.actor.ActorLogging {

  private val sourcesHub: ActorRef =

  override def receive = {
    case Boot(n) => boot(n)
  }

  def booting = {

  }

  def normalOperation = {
    case msg: GetHashesSources => sender() ! hashesSources
    case Shutdown() => {
      log.info("shutting down actor system!")
      context.system.terminate()
    }
  }

  def boot(hashesCount: Int): Unit = {

  }

}

object RootActor {
  final case class GetHashesSources()
  final case class Boot(sourcesCount: Int)
  final case class Shutdown()
}
