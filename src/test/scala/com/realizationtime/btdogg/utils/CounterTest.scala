package com.realizationtime.btdogg.utils

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.Queue

class CounterTest extends FlatSpec with Matchers {

  "Queue" should "have proper takeWhile" in {
    var queue = Queue[Int]()
    queue = queue.enqueue(1)
    queue = queue.enqueue(2)
    queue = queue.enqueue(3)
    val tookWhile = queue.dropWhile(_ < 3)
    tookWhile should have length 1
  }

}
