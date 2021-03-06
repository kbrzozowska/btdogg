package com.realizationtime.btdogg.dhtmanager

import java.nio.file.Files

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.prefixStep
import com.realizationtime.btdogg.BtDoggConfiguration.ScrapingConfig.torrentsTmpDir
import com.realizationtime.btdogg.commons.TKey
import com.realizationtime.btdogg.dhtmanager.DhtLifecycleController.{NodeStopped, StopNode}
import com.realizationtime.btdogg.dhtmanager.DhtsManager.{Boot, BootingPhase, NodeReady, Shutdown, ShutdownCompleted}

class DhtsManager extends Actor with ActorLogging {

  import context._

  private var dhts = Set[NodeReady]()
  Files.createDirectories(torrentsTmpDir)

  override def preStart(): Unit = BtDoggDHTLogger.attach()

  override def receive: Receive = {
    case Boot =>
      val port = HashSourcesConfig.firstPort
      val nodesToProduce = HashSourcesConfig.nodesCount
      val idPrefix = 0
      become(booting(port, nodesToProduce, idPrefix, sender()), discardOld = true)
      self ! NextNode
  }

  private def booting(port: Int, nodesLeft: Int, idPrefix: Int, caller: ActorRef): Receive = {
    def scheduleNextNode = {
      become(booting(port + 1, nodesLeft - 1, (idPrefix + prefixStep) % 256, caller), discardOld = true)
      system.scheduler.scheduleOnce(HashSourcesConfig.nodesCreationInterval, self, NextNode)
    }

    var bootingNodes = Set[ActorRef]()

    {
      case m: BootingPhase => m match {
        case NextNode if nodesLeft <= 0 =>
        // all creations scheduled, waiting for NodeReady objects
        case NextNode =>
          val controller = actorOf(DhtLifecycleController.create(port, idPrefix), "DhtLifecycleController" + port)
          bootingNodes += controller
          log.info(s"starting node $port with prefix: $idPrefix. Nodes left: ${nodesLeft - 1}")
          scheduleNextNode
        case m: NodeReady =>
          dhts += m
          log.info(s"started node ${m.key} on port ${m.port}")
          bootingNodes -= m.lifecycleController
          caller ! m
          if (bootingNodes.isEmpty && nodesLeft < 1) {
            log.info("All DHTs started")
            become(running, discardOld = true)
          }
        case Shutdown =>
          become(shuttingDown(dhts.map(_.lifecycleController) ++ bootingNodes, List(sender())))
      }
    }
  }

  private val running: Receive = {
    case Shutdown => become(shuttingDown(dhts.map(_.lifecycleController), List(sender())))
  }

  def shuttingDown(nodesToShut: Set[ActorRef], shutdownCallers: List[ActorRef]): Receive = {
    log.info("Shutting down DHTs")
    nodesToShut.foreach(_ ! StopNode)

    def waitingForNodesDown(nodesStillAlive: Set[ActorRef], shutdownCallers: List[ActorRef]): Receive = {
      if (nodesStillAlive.isEmpty) {
        shutdownCallers.foreach(_ ! ShutdownCompleted)
        become(stopped)
      }
      val behaviour: Receive = {
        case NodeStopped =>
          become(waitingForNodesDown(nodesStillAlive - sender(), shutdownCallers))
          dhts = dhts.filterNot(_.lifecycleController == sender())
        case ignoreNodeAlreadyToldToStop: NodeReady =>
        case Shutdown =>
          become(waitingForNodesDown(nodesStillAlive, sender() :: shutdownCallers))
      }
      behaviour
    }

    waitingForNodesDown(nodesToShut, shutdownCallers)
  }

  private val stopped: Receive = {
    case Shutdown =>
      sender() ! ShutdownCompleted
  }

  private case object NextNode extends BootingPhase

}

object DhtsManager {

  case object Boot

  sealed trait BootingPhase

  final case class NodeReady(key: TKey, port: Int, lifecycleController: ActorRef, hashesSource: ActorRef, scraping: ActorRef) extends BootingPhase

  sealed trait RunningPhase

  case object Shutdown extends BootingPhase with RunningPhase

  case object ShutdownCompleted

}