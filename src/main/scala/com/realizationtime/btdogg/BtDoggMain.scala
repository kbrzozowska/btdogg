package com.realizationtime.btdogg


import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.realizationtime.btdogg.RootActor.Boot

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BtDoggMain {

  def scheduleShutdown(delay: FiniteDuration) = {
    system.scheduler.scheduleOnce(delay, rootActor, "shutdown")
  }

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
  private implicit val timeout = Timeout(FiniteDuration(10, SECONDS))
  private implicit val log = Logging(system, "btdoggMain")
  private implicit val materializer = ActorMaterializer()
  log.info("Starting btdogg...")
  rootActor ! Boot(BtDoggConfiguration.nodesCount)

  var nodes: List[DhtWrapper] = List()

}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  main.scheduleShutdown(2 minutes) // intended to be ran from scala console
  Await.result(main.system.whenTerminated, Duration.Inf)
}