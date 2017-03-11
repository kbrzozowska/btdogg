package com.realizationtime.btdogg.utils

import scala.concurrent.duration.FiniteDuration

object TimeUtils {

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

}
