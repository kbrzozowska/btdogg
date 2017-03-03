package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging, ActorRef, DeadLetter, Props}
import com.realizationtime.btdogg.RootActor._
import com.realizationtime.btdogg.dhtmanager.DhtsManager
import com.realizationtime.btdogg.dhtmanager.DhtsManager.Shutdown
import com.realizationtime.btdogg.hashessource.SourcesHub
import com.realizationtime.btdogg.hashessource.SourcesHub.AddWorkers
import com.realizationtime.btdogg.scraping.ScrapersHub
import com.realizationtime.btdogg.scraping.ScrapersHub.AddScrapers
import com.realizationtime.btdogg.utils.DeadLetterActor

import scala.language.postfixOps

class RootActor extends Actor with ActorLogging {

  import context._

  private val dhtsManager = actorOf(Props[DhtsManager], "DhtsManager")
  private val sourcesHub = actorOf(Props[SourcesHub], "SourcesHub")
  private val scrapersHub = actorOf(Props[ScrapersHub], "ScrapersHub")

  private def registerDeadLetterActor = {
    val deadLetterActor = system.actorOf(Props.create(classOf[DeadLetterActor]))
    system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])
  }

  override def preStart(): Unit = {
    registerDeadLetterActor
    dhtsManager ! DhtsManager.Boot
  }

  override def receive: Receive = {
    case DhtsManager.BootComplete(nodes) =>
      sourcesHub ! AddWorkers(nodes.map(_.hashesSource))
      val prefixesToScrapers: Map[String, ActorRef] = nodes.map(node => {
        node.key.prefix -> node.scraping
      }).toMap
      scrapersHub ! AddScrapers(prefixesToScrapers)
    case m: Message => m match {
      case SubscribePublisher(p) =>
        sourcesHub ! SourcesHub.SubscribePublisher(p)
      case GetScrapersHub => sender() ! scrapersHub
      case UnsubscribePublisher(sub, msg) =>
        sourcesHub ! SourcesHub.UnsubscribePublisher(sub, msg)
      case ShutdownDHTs =>
        dhtsManager ! Shutdown
        become(stopping(List(sender())))
    }
  }

  private def stopping(shutdownCallers: List[ActorRef]): Receive = {
    case DhtsManager.ShutdownCompleted =>
      shutdownCallers.foreach(_ ! ShutdownComplete)
      become(stopped)
    case ShutdownDHTs => become(stopping(sender() :: shutdownCallers))
  }

  private val stopped: Receive = {
    case ShutdownDHTs => sender() ! ShutdownComplete
  }

}

object RootActor {

  sealed trait Message

  final case class SubscribePublisher(publisher: ActorRef) extends Message

  final case class UnsubscribePublisher(subscriber: ActorRef, endMessage: Option[Any]) extends Message

  case object GetScrapersHub extends Message

  case object ShutdownDHTs extends Message

  case object ShutdownComplete

}
