package com.realizationtime.btdogg


import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.realizationtime.btdogg.BtDoggConfiguration.HashSourcesConfig.nodesCount
import com.realizationtime.btdogg.RootActor.Boot
import com.realizationtime.btdogg.scraping.ScrapingProcess
import redis.RedisClient

import scala.collection.immutable.Queue
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode

class BtDoggMain {

  def scheduleShutdown(delay: FiniteDuration) = {
    system.scheduler.scheduleOnce(delay, rootActor, RootActor.Shutdown())
  }

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
  //  private implicit val timeout: Timeout = Timeout(nodesCreationInterval * nodesCount * 3)
  private implicit val log = Logging(system, classOf[BtDoggMain])
  private val decider: Supervision.Decider = { e =>
    log.error(e, "Unhandled exception in stream")
    Supervision.resume
  }
  private val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  private implicit val materializer = ActorMaterializer(materializerSettings)
  log.info("Starting btdogg...")

  val scrapingProcess = new ScrapingProcess(
    checkIfKnownDB = RedisClient(db = Some(1)),
    hashesBeingScrapedDB = RedisClient(db = Some(2))
  )

  val window = 3000
  private val startTime = System.nanoTime()
  private val publisher = scrapingProcess.onlyNewHashes
    .toMat(Sink.fold((0L, Queue[Long](startTime)))((u, k) => {
      val suchNow = System.nanoTime()
      val (oldI, oldHistory) = u
      val (n, time, newHistory) =
        if (oldHistory.length < window)
          (oldHistory.length + 1, oldHistory.front, oldHistory.enqueue(suchNow))
        else {
          val (time, dequeued) = oldHistory.dequeue
          (oldHistory.length, time, dequeued.enqueue(suchNow))
        }
      val i = oldI + 1
      val rate: BigDecimal = BigDecimal(n) / (BigDecimal(suchNow - time) / BigDecimal(1000000000L))
      val rateScaled = rate.setScale(3, RoundingMode.HALF_UP)
      println(s"$i. $rateScaled/s $k")
      (i, newHistory)
    }))(Keep.left)
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