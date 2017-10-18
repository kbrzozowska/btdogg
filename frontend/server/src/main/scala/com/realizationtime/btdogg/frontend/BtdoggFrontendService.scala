package com.realizationtime.btdogg.frontend

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import com.realizationtime.btdogg.commons.TKey

import scala.concurrent.ExecutionContext

trait BtdoggFrontendService extends Config with JsonSupport {

  protected implicit val executionContext: ExecutionContext

  protected val mongoFetcher: MongoFetcher

  protected lazy val routes: Route =
    pathPrefix("api" / "v1") {
        pathPrefix("torrent") {
          path(Segment) { hash: String =>
            pathEnd {
              get {
                val maybeTorrent = mongoFetcher.fetch(TKey(hash))
                  .map(EmberView(_))
                complete(maybeTorrent)
              }
            }
          }
        }
    }

  def stop() = mongoFetcher.stop()

}
