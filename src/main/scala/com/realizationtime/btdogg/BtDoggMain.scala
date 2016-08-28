package com.realizationtime.btdogg


import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.pattern.AskableActorRef
import akka.util.Timeout
import com.realizationtime.btdogg.HashesSource.GetAllKeys
import com.realizationtime.btdogg.RootActor.GetHashesSource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class BtDoggMain {

  def scheduleShutdown: Unit = {
    system.scheduler.scheduleOnce(10 seconds, rootActor, "shutdown")
  }

  def getAllKeys: Future[Set[TKey]] = {
    rootActorAskable ? GetAllKeys() map {
      case response: Set[Any] => response
        .map({ case r: TKey => r })
    }
  }

  def getAllKeysAsStrings: Future[String] = getAllKeys map {
    case keys => keys.mkString(",\n")
  }

  def printAllKeys: Unit = getAllKeysAsStrings.foreach(println(_))

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
  private implicit val timeout = Timeout(FiniteDuration(10, SECONDS))
  private implicit val log = Logging(system, "btdoggMain")
  log.info("Starting btdogg...")

  val rootActorAskable: AskableActorRef = new AskableActorRef(rootActor)
  rootActorAskable ? GetHashesSource() onComplete {
    case hashesSource: Try[Any] => hashesSource.get match {
      case hashesSource: ActorRef => new DhtWrapper(hashesSource)
    }
  }
}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  main.scheduleShutdown // intended to be ran from scala console
  Await.result(main.system.whenTerminated, Duration.Inf)
}