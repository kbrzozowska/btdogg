package com.realizationtime.btdogg.commons.mongo

import java.time.{Instant, LocalDate}

import com.realizationtime.btdogg.commons.mongo.MongoTorrent.Liveness
import com.realizationtime.btdogg.commons.{FileEntry, TKey}

case class MongoTorrent(_id: TKey,
                        title: Option[String],
                        totalSize: Long,
                        data: List[FileEntry],
                        creation: Instant = Instant.now(),
                        modification: Instant,
                        liveness: Liveness = Liveness()) {
  def key: TKey = _id
}

object MongoTorrent {

  case class Liveness(requests: Map[LocalDate, Int] = Map(),
                      announces: Map[LocalDate, Int] = Map())

}