package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging, ActorRef, DeadLetter, Props}
import com.realizationtime.btdogg.RootActor.SubscribePublisher
import com.realizationtime.btdogg.dhtmanager.DhtsManager
import com.realizationtime.btdogg.hashessource.SourcesHub
import com.realizationtime.btdogg.hashessource.SourcesHub.AddWorkers
import com.realizationtime.btdogg.utils.DeadLetterActor

import scala.language.postfixOps

class RootActor extends Actor with ActorLogging {

  import context._

  private val sourcesHub = context.actorOf(Props[SourcesHub], "sourcesHub")
  private val dhtsManager = context.actorOf(Props[DhtsManager], "DhtsManager")

  def registerDeadLetterActor = {
    val deadLetterActor = system.actorOf(Props.create(classOf[DeadLetterActor]))
    system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])
  }


  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    registerDeadLetterActor
    dhtsManager ! DhtsManager.Boot
  }

  override def receive: Receive = {
    case DhtsManager.BootComplete(nodes) =>
      sourcesHub ! AddWorkers(nodes.map(_.hashesSource))
    case SubscribePublisher(p) =>
      sourcesHub ! SourcesHub.SubscribePublisher(p)
  }

}

object RootActor {

  case class SubscribePublisher(publisher: ActorRef)

  //  final case class Shutdown()

}
