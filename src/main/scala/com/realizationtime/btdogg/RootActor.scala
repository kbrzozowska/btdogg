package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging, ActorRef, DeadLetter, Props}
import com.realizationtime.btdogg.RootActor.{GetScrapersHub, Message, SubscribePublisher}
import com.realizationtime.btdogg.dhtmanager.DhtsManager
import com.realizationtime.btdogg.hashessource.SourcesHub
import com.realizationtime.btdogg.hashessource.SourcesHub.AddWorkers
import com.realizationtime.btdogg.scraping.ScrapersHub
import com.realizationtime.btdogg.scraping.ScrapersHub.AddScrapers
import com.realizationtime.btdogg.utils.DeadLetterActor

import scala.language.postfixOps

class RootActor extends Actor with ActorLogging {

  import context._

  private val dhtsManager = context.actorOf(Props[DhtsManager], "DhtsManager")
  private val sourcesHub = context.actorOf(Props[SourcesHub], "SourcesHub")
  private val scrapersHub = context.actorOf(Props[ScrapersHub], "ScrapersHub")

  def registerDeadLetterActor = {
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
    }
  }

}

object RootActor {
  sealed trait Message
  final case class SubscribePublisher(publisher: ActorRef) extends Message
  case object GetScrapersHub extends Message
  //  final case class Shutdown()

}
