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
      previousI = i
      oldHistory = newHistory
      Tick(i, Rate(n, suchNow - time), t)
    }
  }

  def apply[T](window: Duration): (T) => Tick[T] = nanosecondsWindow(window.toNanos)

  def apply[T](window: scala.concurrent.duration.Duration): (T) => Tick[T] = nanosecondsWindow(window.toNanos)

  def apply[T](window: scala.concurrent.duration.Duration, clock: () => Long): (T) => Tick[T] = nanosecondsWindow(window.toNanos, clock)

  def nanosecondsWindow[T](nanoWindow: Long, clock: () => Long = () => {
    System.nanoTime()
  }): (T) => Tick[T] = {
    val startTime = clock()
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

    def trimQueue(suchNow: Long) = {
      import scala.collection.Searching._
      val windowBorder = suchNow - nanoWindow
      val removeUntilIndex = ticksQueue.search(windowBorder).insertionPoint
      ticksQueue = ticksQueue drop removeUntilIndex
    }

    (t: T) => {
      val suchNow = clock()
      trimQueue(suchNow)
      ticksQueue = ticksQueue :+ suchNow
      previousI = previousI + 1
      Tick(previousI, Rate(ticksQueue.length, timeNanos()), t)
    }
  }

  final case class Tick[T](i: Long, rate: Rate, item: T) {
    def apply(): T = item

    override def toString: String = s"$i. $rate/s $item"
  }

  final case class Rate(ticksCount: Long, timeNanos: Long) {
    lazy val rateUnscaled: BigDecimal = BigDecimal(ticksCount) / (BigDecimal(timeNanos) / BigDecimal(1000000000L))
    lazy val rate: BigDecimal = rateUnscaled.setScale(3, RoundingMode.HALF_UP)

    def apply(): BigDecimal = rate

    override def toString: String = {
      rate.toString()
    }
  }

}
