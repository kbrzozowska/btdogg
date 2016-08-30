package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.realizationtime.btdogg.BtDoggConfiguration.nodesCreationInterval
import com.realizationtime.btdogg.RootActor.{Boot, Shutdown}
import com.realizationtime.btdogg.SourcesHub.{Init, NodeStarted, StartNode}

class RootActor extends Actor with ActorLogging {

  import context._

  private val sourcesHub: ActorRef = context.actorOf(Props[SourcesHub], "sourcesHub")
  private val hashesPublisher: ActorRef = context.actorOf(Props[HashesPublisher], "hashesPublisher")
  override def receive = {
    case Boot(nodesCount) =>
      context.become(booting(nodesCount), discardOld = true)
      sourcesHub ! Init(hashesPublisher)
      orderNewNode(nodesCount)
  }

  def booting(nodesNotReady: Int): Receive = {
    val bootingBehaviour: Receive = {
      case Boot(_) =>
      case BootNext(nodesCount) =>
        orderNewNode(nodesCount)
      case NodeStarted(portNumber) =>
        log.info(s"Starting of node $portNumber completed")
        val nodesStillNotReady = nodesNotReady - 1
        if (nodesStillNotReady == 0)
          become(normalOperation, discardOld = true)
        else
          become(booting(nodesStillNotReady), discardOld = true)
      case msg =>
        self forward msg
    }
    bootingBehaviour
  }

  import scala.concurrent.duration._
  def normalOperation: Receive = {
    case Shutdown() =>
      log.info("Ordering shutdown of dht nodes")
      sourcesHub ! SourcesHub.Stop()
      become(shuttingDown)
      context.system.scheduler.scheduleOnce(10 seconds, self, Shutdown())
  }

  def shuttingDown: Receive = {
    case Shutdown() =>
      log.info("shutting down actor system!")
      context.system.terminate()
  }

  def orderNewNode(nodesCount: Int) = {
    log.info("Starting node")
    sourcesHub ! StartNode()
    if (nodesCount > 1) {
      log.info(s"${nodesCount - 1} nodes left to start")
      context.system.scheduler.scheduleOnce(nodesCreationInterval, self, BootNext(nodesCount - 1))
    }
  }

  private final case class BootNext(nodesCount: Int)

}

object RootActor {
  final case class Boot(sourcesCount: Int)
  final case class Shutdown()
}
