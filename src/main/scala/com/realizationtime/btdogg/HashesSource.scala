package com.realizationtime.btdogg

import akka.actor.Actor
import akka.stream.actor.ActorPublisher
import com.realizationtime.btdogg.HashesSource.GetAllKeys

class HashesSource extends Actor with ActorPublisher[TKey] with akka.actor.ActorLogging {

  var keys = Set[TKey]()

  override def receive: Receive = {
    case key: TKey => {
      if (!keys.contains(key)) {
        keys += key
        log.info(s"New key: $key")
        onNext(key)
      }
    }
    case GetAllKeys => sender() ! keys
  }

}

object HashesSource {
  final case class GetAllKeys()
}