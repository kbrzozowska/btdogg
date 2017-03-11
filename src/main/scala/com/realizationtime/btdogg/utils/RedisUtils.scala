package com.realizationtime.btdogg.utils

import akka.NotUsed
import akka.stream.scaladsl.Source
import redis.{Cursor, RedisClient}

import scala.concurrent.{ExecutionContext, Future}

object RedisUtils {

  private implicit val deserializer = redis.ByteStringDeserializer.String

  def streamAll(redisClient: RedisClient)(implicit ec: ExecutionContext): Source[KV, NotUsed] = {
    val startIndex: Option[Int] = None
    val portions: Source[Seq[String], NotUsed] = Source.unfoldAsync(startIndex)((cur: Option[Int]) => {
      if (cur.contains(0))
        Future.successful(None)
      else redisClient.scan(cursor = cur.getOrElse(0))
        .map((cur: Cursor[Seq[String]]) => {
          val retFromFold: (Option[Int], Seq[String]) = (Some(cur.index), cur.data)
          Some(retFromFold)
        })
    })
    val flat: Source[KV, NotUsed] = portions.mapConcat(el => el.toList)
      .mapAsyncUnordered(10)(key => redisClient.get(key).map(_.map(value => key -> value)))
      .filter(_.isDefined)
      .map(_.get)
      .map(KV(_))
    flat
  }

  case class KV(key: String, value: String)
  object KV {
    def apply(pair: (String, String)): KV = KV(pair._1, pair._2)
  }

}
