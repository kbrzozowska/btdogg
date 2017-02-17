package com.realizationtime.btdogg.utils

import akka.actor.{Actor, DeadLetter}

class DeadLetterActor extends Actor {

  override def receive: Receive = {
    case m: DeadLetter => println(s"Dead letter: $m")
  }

}
