package com.realizationtime.btdogg

import akka.actor.Actor
import com.realizationtime.btdogg.HashesSource.GetAllKeys

class HashesSource extends Actor with akka.actor.ActorLogging {

  var keys = Set[TKey]()

  override def receive: Receive = {
    case key: TKey => {
      if (!keys.contains(key)) {
        keys += key
        log.info(s"New key: $key")
      }
    }
    case GetAllKeys => sender() ! keys
  }

}

object HashesSource {
  final case class GetAllKeys()
}