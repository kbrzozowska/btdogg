package com.realizationtime.btdogg.utils

import akka.NotUsed
import akka.stream.scaladsl.Source
import redis.{Cursor, RedisClient}

import scala.concurrent.{ExecutionContext, Future}

object RedisUtils {

  private implicit val deserializer = redis.ByteStringDeserializer.String

  def streamAll(redisClient: RedisClient)(implicit ec: ExecutionContext): Source[KV, NotUsed] = {
    val startState = FoldingState()
    val keys: Source[String, NotUsed] = Source.unfoldAsync(startState)((cur: FoldingState) => {
      cur.fold(redisClient)
    })
    val flat: Source[KV, NotUsed] = keys
      .mapAsyncUnordered(10)(key => {
      redisClient.get(key).map(_.map(value => key -> value))
    })
      .filter(_.isDefined)
      .map(_.get)
      .map(KV(_))
    flat
  }

  case class KV(key: String, value: String)

  object KV {
    def apply(pair: (String, String)): KV = KV(pair._1, pair._2)
  }

  private case class FoldingState(cursor: Option[Int] = None, portion: Seq[String] = Seq.empty, nextElementIndex: Int = 0) {

    def fold(redisClient: RedisClient)(implicit ec: ExecutionContext): Future[Option[(FoldingState, String)]] = {
      if (portion.nonEmpty)
        Future.successful(Some((FoldingState(cursor, nextPortion(), incrementNextElementIndex()), portion(nextElementIndex))))
      else if (cursor.contains(0))
        Future.successful(None)
      else redisClient.scan(cursor = cursor.getOrElse(0))
        .map((cur: Cursor[Seq[String]]) => {
          val data = cur.data
          if (data.isEmpty)
            None
          else {
            val retFromFold: (FoldingState, String) = if (data.size == 1)
              (FoldingState(Some(cur.index)), data.head)
              else
              (FoldingState(Some(cur.index), cur.data, 1), data.head)
            Some(retFromFold)
          }
        })
    }

    private def nextPortion(): Seq[String] =
      if (isLast)
        Seq.empty
      else
        portion

    private def isLast = {
      nextElementIndex + 1 == portion.size
    }

    private def incrementNextElementIndex(): Int =
      if (isLast)
        0
      else
        nextElementIndex + 1

  }

}
