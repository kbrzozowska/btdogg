package com.realizationtime.btdogg


import java.util.concurrent.TimeUnit.SECONDS
import java.util.zip.ZipConstants64

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.pattern.AskableActorRef
import akka.stream.scaladsl.GraphDSL.Builder
import akka.stream.scaladsl.{GraphDSL, Keep, Merge, RunnableGraph, Sink, Source, Zip, ZipWith, ZipWith2}
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy, SourceShape}
import akka.util.Timeout
import com.realizationtime.btdogg.HashesSource.GetAllKeys
import com.realizationtime.btdogg.RootActor.GetHashesSources
import com.sun.org.apache.xerces.internal.util.DOMErrorHandlerWrapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class BtDoggMain {

  def scheduleShutdown(delay: FiniteDuration) = {
    system.scheduler.scheduleOnce(delay, rootActor, "shutdown")
  }

  def getAllKeys: Future[Set[TKey]] = {
    rootActorAskable ? GetAllKeys() map {
      case response: Set[Any] => response
        .map({ case r: TKey => r })
    }
  }

  def getAllKeysAsStrings: Future[String] = getAllKeys map {
    case keys => keys.mkString(",\n")
  }

  def printAllKeys: Unit = getAllKeysAsStrings.foreach(println(_))

  private implicit val system = ActorSystem("BtDogg")
  private val rootActor = system.actorOf(Props[RootActor], "rootActor")
  private implicit val timeout = Timeout(FiniteDuration(10, SECONDS))
  private implicit val log = Logging(system, "btdoggMain")
  private implicit val materializer = ActorMaterializer()
  log.info("Starting btdogg...")

  val rootActorAskable: AskableActorRef = new AskableActorRef(rootActor)
  var nodes: List[DhtWrapper] = List()

  rootActorAskable ? GetHashesSources() onComplete {
    case hashesSources: Try[Any] => hashesSources.get match {
      case hashesSources: List[ActorRef] => createNodes(hashesSources)
    }
  }

  def createNodes(hashesSources: List[ActorRef]): List[DhtWrapper] = {
    val ticks = Source.tick(Duration.Zero, 500 millis, "tick")
    val sources = Source(hashesSources.zipWithIndex)

    type IndexedSource = (ActorRef, Int)

    val tickedSources: Source[IndexedSource, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val zip = builder.add(ZipWith[String, IndexedSource, IndexedSource]((tick, actor) => actor))
      ticks ~> zip.in0
      sources ~> zip.in1
      SourceShape(zip.out)
    })
    val nodes: Future[List[DhtWrapper]] = tickedSources
      .map(createNode)
      .runWith(Sink.fold(List[DhtWrapper]())((list, node) => node :: list))
    nodes onSuccess {
      case nodes: List[DhtWrapper] => this.nodes = nodes
    }
  }

  def createNode(fTuple: Tuple2[ActorRef, Int]): DhtWrapper = {
    new DhtWrapper(fTuple._1, BtDoggConfiguration.firstPort + fTuple._2)
  }

  /* My failed experiments on using streams
  def usingStreams = {
    val sources: List[Source[TKey, ActorRef]] = List.range(0, BtDoggConfiguration.nodesCount)
      .map(i => Source.actorRef[TKey](100, OverflowStrategy.dropNew))
    val sink = Sink.foreach(println)
    val flow = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val merge = builder.add(Merge[TKey](2))
      sources.head ~> merge ~> sink
      sources.tail.head ~> merge
      ClosedShape
    })
//    val sourcesMerged = Source.fromGraph(GraphDSL.create() { (builder: Builder[ActorRef]) =>
//      import GraphDSL.Implicits._
//      val merge = builder.add(new Merge[TKey](sources.length))
//      sources
//        .foreach(_._2 ~> merge)
//      SourceShape
//    })
//    val merged: Source[TKey, NotUsed] = Source.combine(sources.head, sources.tail.head)(Merge(_))

  }*/

}

object BtDoggMain extends App {
  private val main = new BtDoggMain
  main.scheduleShutdown(10 seconds) // intended to be ran from scala console
  Await.result(main.system.whenTerminated, Duration.Inf)
}