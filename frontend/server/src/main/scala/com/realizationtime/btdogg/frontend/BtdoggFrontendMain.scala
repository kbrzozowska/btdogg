package com.realizationtime.btdogg.frontend

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object BtdoggFrontendMain extends App with Config with BtdoggFrontendService {

  lazy val log = Logging(system, BtdoggFrontendMain.getClass)

  implicit val system: ActorSystem = ActorSystem("btdoggFrontendHttpServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  override protected val mongoFetcher = new MongoFetcher(mongoConfig.uri)

  val debugging = DebuggingDirectives.logRequestResult("kurwa")(routes)

  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(debugging, httpConfig.interface, httpConfig.port)
  println(s"Server online at ${httpConfig.interface}:${httpConfig.port}/\nPress RETURN to stop...")
  StdIn.readLine()
  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      mongoFetcher.stop()
      done.failed.map { ex => log.error(ex, "Failed unbinding") }
      system.terminate()
    }
}
