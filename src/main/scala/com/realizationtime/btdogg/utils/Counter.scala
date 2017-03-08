package com.realizationtime.btdogg.utils

import scala.collection.immutable.Queue
import scala.math.BigDecimal.RoundingMode

object Counter {
  def apply[T](window: Int): (T) => Tick[T] = {
    val startTime = System.nanoTime()
    var previousI = 0L
    var oldHistory = Queue[Long](startTime)
    (t: T) => {
      val suchNow = System.nanoTime()
      val (n, time, newHistory) =
        if (oldHistory.length < window)
          (oldHistory.length + 1, oldHistory.front, oldHistory.enqueue(suchNow))
        else {
          val (time, dequeued) = oldHistory.dequeue
          (oldHistory.length, time, dequeued.enqueue(suchNow))
        }
      val i = previousI + 1
      val rate: BigDecimal = BigDecimal(n) / (BigDecimal(suchNow - time) / BigDecimal(1000000000L))
      val rateScaled = rate.setScale(3, RoundingMode.HALF_UP)
      previousI = i
      oldHistory = newHistory
      Tick(i, rateScaled, t)
    }
  }

  final case class Tick[T](i: Long, rate: BigDecimal, item: T) {
    def apply(): T = item
  }

}
