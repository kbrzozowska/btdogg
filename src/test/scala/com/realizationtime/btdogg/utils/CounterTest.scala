package com.realizationtime.btdogg.utils

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.Queue
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

class CounterTest extends FlatSpec with Matchers {

  "Queue" should "have proper takeWhile" in {
    var queue = Queue[Int]()
    queue = queue.enqueue(1)
    queue = queue.enqueue(2)
    queue = queue.enqueue(3)
    val tookWhile = queue.dropWhile(_ < 3)
    tookWhile should have length 1
  }

  //"Counter"
  ignore should "count properly" in {
    import scala.concurrent.duration._
    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    //    println(s"max frequency: ${system.scheduler.getClass}")

    val start = Instant.now()

    val fut = Source.tick(Duration.Zero, 1 millisecond, "a")
      //      .take(1000000)
      .limit(500000)
      .map(Counter(2 minutes))
      //      .zipWithIndex
      //      .filter(_.i % 1000L == 0)
      .filter(t => t.i % 1000L == 0)
      //      .map(t => {
      //        Instant.now()
      //      })
      //      .sliding(2)
      //      .runForeach { case Vector(start, end) =>
      //        val dur = java.time.Duration.between(start, end)
      //        println(dur)
      //      }
      .runForeach(t => {
      val now = Instant.now()
//      println(s"$now $t ${java.time.Duration.between(start, now)}")
      println(s"${java.time.Duration.between(start, now).getSeconds} ${t.rate.toInt}")
    })
    Await.ready(fut, Duration.Inf)
  }

}
