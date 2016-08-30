package com.realizationtime.btdogg

import akka.actor.{Actor, ActorLogging}

class HashesPublisher extends Actor with ActorLogging {
  override def receive: Receive = {
    case any => log.info(s"$any")
  }
}
