package com.realizationtime.btdogg


import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import akka.util.Timeout
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.{nodesCount, nodesCreationInterval}
import com.realizationtime.btdogg.RootActor.Boot
import com.realizationtime.btdogg.scraping.ScrapingProcess
import redis.RedisClient

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class BtDoggMain {

  def scheduleShutdown(delay: FiniteDuration) = {
    system.scheduler.scheduleOnce(delay, rootActor, RootActor.Shutdown())
  }

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
  private implicit val timeout: Timeout = Timeout(nodesCreationInterval * nodesCount * 15 / 10)
  private implicit val log = Logging(system, "btdoggMain")
  private implicit val materializer = ActorMaterializer()
  log.info("Starting btdogg...")

  val scrapingProcess = new ScrapingProcess(
    new RedisClient(db = Some(1)),
    new RedisClient(db = Some(2))
  )
  val publisher = scrapingProcess.onlyNewHashes
      .toMat(Sink.foreach(k => println(s"kaka $k")))(Keep.left)
      .run()
  rootActor ! Boot(nodesCount, publisher)

}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  println("#### Press Enter to shut system down")
  scala.io.StdIn.readLine()
  main.scheduleShutdown(0 minutes)
  Await.result(main.system.whenTerminated, Duration.Inf)
}