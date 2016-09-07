package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig
import com.realizationtime.btdogg.HashesSource.{Start, StartingCompleted}
import com.realizationtime.btdogg.SourcesHub.{Init, NodeStarted, StartNode, Stop}

class SourcesHub extends Actor with ActorLogging {

  override def receive: Receive = {
    case Init(publisher) =>
      context.become(working(publisher, startingState), discardOld = true)
  }

  def working(hashesPublisher: ActorRef, state: State):Receive = {
    {
      case k: TKey =>
        hashesPublisher forward k
      case StartNode() =>
        context.become(working(hashesPublisher, state.startNode), discardOld = true)
      case StartingCompleted(node) =>
        context.become(working(hashesPublisher, state.nodeStarted(node)), discardOld = true)
      case Stop() =>
        (state.activeNodes ++ state.startingNodesToRequesters.keySet)
          .foreach(_ ! HashesSource.Stop())
    }
  }

  val startingState = new State(List(), Map(), Map())
  class State(
                       val activeNodes: List[ActorRef],
                       val startingNodesToRequesters: Map[ActorRef, ActorRef],
                       val portsToNodes: Map[Int, ActorRef]
                     ) {
    lazy val nodesToPorts = portsToNodes.map(_.swap)
    def startNode: State = {
      val portNumber = getFreePort
      val node = context.actorOf(Props[HashesSource], "HashesSource" + portNumber)
      val newState = new State(
        activeNodes,
        startingNodesToRequesters + (node -> sender),
        portsToNodes + (portNumber -> node)
      )
      node ! Start(portNumber)
      newState
    }

    def nodeStarted(node:ActorRef): State = {
      startingNodesToRequesters(node) ! NodeStarted(nodesToPorts(node))
      new State(
        node :: activeNodes,
        startingNodesToRequesters - node,
        portsToNodes
      )
    }

    private def getFreePort = {
      val availablePorts = List.range(HashSourcesConfig.firstPort, HashSourcesConfig.lastPort + 1, 1)
      availablePorts
        .filterNot(portsToNodes.keySet.contains(_))
        .min
    }
  }
}

object SourcesHub {
  final case class Init(HashesPublisher: ActorRef)
  final case class StartNode()
  final case class NodeStarted(portNumber: Int)
  final case class Stop()
}