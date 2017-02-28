package com.realizationtime.btdogg.dhtmanager

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig
import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.dhtmanager.DhtsManager.{Boot, BootComplete, BootingPhase, NodeReady}

class DhtsManager extends Actor with ActorLogging {
  private var dhts = Set[NodeReady]()

  override def receive: Receive = {
    case Boot =>
      val port = HashSourcesConfig.firstPort
      val nodesToProduce = HashSourcesConfig.nodesCount
      val idPrefix = 0
      context.become(booting(port, nodesToProduce, idPrefix, sender()), discardOld = true)
      self ! NextNode
  }

  def booting(port: Int, nodesLeft: Int, idPrefix: Int, caller: ActorRef): Receive = {
    def scheduleNextNode = {
      context.become(booting(port + 1, nodesLeft - 1, (idPrefix + 1) % 256, caller), discardOld = true)
      import scala.concurrent.ExecutionContext.Implicits.global
      context.system.scheduler.scheduleOnce(HashSourcesConfig.nodesCreationInterval, self, NextNode)
    }

    var bootingNodes = Set[ActorRef]()

    {
      case m: BootingPhase => m match {
        case NextNode if nodesLeft <= 0 =>
        // all creations scheduled
        case NextNode =>
          val controller = context.actorOf(DhtLifecycleController.create(port, idPrefix), "DhtLifecycleController" + port)
          bootingNodes += controller
          scheduleNextNode
        case m: NodeReady =>
          dhts += m
          bootingNodes -= m.lifecycleController
          if (bootingNodes.isEmpty && nodesLeft < 1) {
            log.info("All DHTs started")
            context.become(running, discardOld = true)
            caller ! BootComplete(dhts)
          }
      }
    }
  }

  val running: Receive = Actor.ignoringBehavior

  private case object NextNode extends BootingPhase

}

object DhtsManager {

  case object Boot

  final case class BootComplete(nodes: Set[NodeReady])

  sealed abstract trait BootingPhase

  final case class NodeReady(key: TKey, lifecycleController: ActorRef, hashesSource: ActorRef, scraping: ActorRef) extends BootingPhase

}