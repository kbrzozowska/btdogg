package com.realizationtime.btdogg.hashessource

import akka.actor.{Actor, ActorLogging}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import com.realizationtime.btdogg.TKey

import scala.annotation.tailrec

class HashesPublisher extends Actor with ActorPublisher[TKey] with ActorLogging {

  val MaxBufferSize = 100
  val BufferOverflowWarningLevel = BigDecimal("0.9")
  var buf: Vector[TKey] = Vector.empty[TKey]

  override def receive: Receive = {
    case k: TKey if buf.length == MaxBufferSize =>
      log.warning(s"dropping received hash $k due to buffer overflow")
    case key: TKey =>
      log.debug(s"received hash $key")
      if (buf.isEmpty && totalDemand > 0)
        onNext(key)
      else {
        buf = buf :+ key // somehow "buf += key" does not work
        deliverBuf()
      }
    case Request(_) =>
      deliverBuf()
    case Cancel =>

  }

  final def deliverBuf(): Unit = {
    @tailrec def deliverBufRec(): Unit =
      if (totalDemand > 0) {
        /*
         * totalDemand is a Long and could be larger than
         * what buf.splitAt can accept
         */
        if (totalDemand <= Int.MaxValue) {
          val (use, keep) = buf.splitAt(totalDemand.toInt)
          buf = keep
          use foreach onNext
        } else {
          val (use, keep) = buf.splitAt(Int.MaxValue)
          buf = keep
          use foreach onNext
          deliverBufRec()
        }
      }
    deliverBufRec()
    logIfBufferCloseToOverflow
  }

  var dontSpamConsoleCounter = 0L
  val DontSpamConsoleInterval = 20

  def logIfBufferCloseToOverflow: Unit = {
    if (BigDecimal(buf.size) / BigDecimal(MaxBufferSize) >= BufferOverflowWarningLevel) {
      if (dontSpamConsoleCounter % DontSpamConsoleInterval == 0)
        log.info(s"hashes buffer contains ${buf.size} elements of total $MaxBufferSize capacity")
      dontSpamConsoleCounter += 1
    } else
      dontSpamConsoleCounter = 0
  }

}
