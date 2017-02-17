package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging, ActorRef, DeadLetter, Props}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.nodesCreationInterval
import com.realizationtime.btdogg.RootActor.{Boot, Shutdown}
import com.realizationtime.btdogg.hashessource.SourcesHub
import com.realizationtime.btdogg.hashessource.SourcesHub.{Init, NodeStarted, StartNode}
import com.realizationtime.btdogg.utils.DeadLetterActor

import scala.language.postfixOps

class RootActor extends Actor with ActorLogging {

  import context._

  private val sourcesHub: ActorRef = context.actorOf(Props[SourcesHub], "sourcesHub")
  private var hashesPublisher: ActorRef = _

  def registerDeadLetterActor = {
    val deadLetterActor = system.actorOf(Props.create(classOf[DeadLetterActor]))
    system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])
  }

  override def receive = {
    case Boot(nodesCount, publisher) =>
      this.hashesPublisher = publisher
      registerDeadLetterActor
      context.become(booting(nodesCount), discardOld = true)
      sourcesHub ! Init(hashesPublisher)
      orderNewNode(nodesCount)
  }

  def booting(nodesNotReady: Int): Receive = {
    val bootingBehaviour: Receive = {
      case Boot(_, _) =>
      case BootNext(nodesCount) =>
        orderNewNode(nodesCount)
      case NodeStarted(portNumber) =>
        log.debug(s"Starting of node $portNumber completed")
        val nodesStillNotReady = nodesNotReady - 1
        if (nodesStillNotReady == 0) {
          become(normalOperation, discardOld = true)
          log.info("All hash sources started")
        } else
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
    log.debug("Starting node")
    sourcesHub ! StartNode()
    if (nodesCount > 1) {
      log.info(s"${nodesCount - 1} nodes left to start")
      context.system.scheduler.scheduleOnce(nodesCreationInterval, self, BootNext(nodesCount - 1))
    }
  }

  private final case class BootNext(nodesCount: Int)

}

object RootActor {

  final case class Boot(sourcesCount: Int, hashesPublisher: ActorRef)

  final case class Shutdown()

}
