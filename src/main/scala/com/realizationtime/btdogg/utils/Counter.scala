package com.realizationtime.btdogg.utils

import java.time.Duration

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

  def apply[T](window: Duration): (T) => Tick[T] = nanosecondsWindow(window.toNanos)

  def apply[T](window: scala.concurrent.duration.Duration): (T) => Tick[T] = nanosecondsWindow(window.toNanos)

  def apply[T](window: scala.concurrent.duration.Duration, clock: () => Long): (T) => Tick[T] = nanosecondsWindow(window.toNanos, clock)

  def nanosecondsWindow[T](nanoWindow: Long, clock: () => Long = () => {
    System.nanoTime()
  }): (T) => Tick[T] = {
    val startTime = System.nanoTime()
    var ticksQueue: Vector[Long] = Vector[Long]()
    var previousI = 0L

    def timeNanos(): Long = {
      val now = System.nanoTime()
      val candidate = {
        val sinceStart = now - startTime
        if (ticksQueue.isEmpty)
          sinceStart
        else
          List(nanoWindow, sinceStart).min
      }
      if (candidate == 0L)
        1000000000L
      else
        candidate
    }

    (t: T) => {
      val suchNow = System.nanoTime()
      val windowBorder = suchNow - nanoWindow
      ticksQueue = ticksQueue :+ suchNow dropWhile (_ < windowBorder)
      val i = previousI + 1
      val rate: BigDecimal = BigDecimal(ticksQueue.length) / (BigDecimal(timeNanos()) / BigDecimal(1000000000L))
      val rateScaled = rate.setScale(3, RoundingMode.HALF_UP)
      previousI = i
      Tick(i, rateScaled, t)
    }
  }

  final case class Tick[T](i: Long, rate: BigDecimal, item: T) {
    def apply(): T = item

    override def toString: String = s"$i. $rate/s $item"
  }

}
