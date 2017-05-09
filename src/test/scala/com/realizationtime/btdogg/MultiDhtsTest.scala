package com.realizationtime.btdogg

import com.realizationtime.btdogg.MultiDhtsTest.{CheckStackTrace, NaggerDuckException, ToMock}
import lbms.plugins.mldht.kad.DHT
import org.mockito.Mockito
import org.mockito.Mockito.{verifyNoMoreInteractions, when, withSettings}
import org.scalatest.{FlatSpec, Ignore, Matchers}
import the8472.mldht.TorrentFetcher

import scala.collection.JavaConverters

/**
  * You can safely delete this class, dude.
  * It was used for an experiment.
  */
@Ignore
class MultiDhtsTest extends FlatSpec with Matchers {

//  private val dht1 = Mockito.mock(classOf[DHT], withSettings().verboseLogging())
  private val dht1 = Mockito.mock(classOf[DHT])
//  private val dht2 = Mockito.mock(classOf[DHT], withSettings().verboseLogging())
  private val dht2 = Mockito.mock(classOf[DHT])
  private val dhts: List[DHT] = List(dht1, dht2)
  private val k1 = TKey("00112233445566778899AABBCCDDEEFF00112233")

  "MultiDhtTorrentFetcher" should "be able to be created" in {
    val fetcher = new TorrentFetcher(JavaConverters.asJavaCollection(dhts))
    dhts
      .foreach(dht =>
//        when(dht.isRunning).thenReturn(true)
        when(dht.isRunning).thenThrow(new NaggerDuckException)
      )
    fetcher.fetch(k1.mldhtKey)
//    scala.concurrent.duration.MILLISECONDS.sleep(500)
    verifyNoMoreInteractions(dht1)
    verifyNoMoreInteractions(dht2)
  }

//  /*"ToMock"*/ ignore should "print stack trace if mocked" in {
//    val mocked = Mockito.mock(classOf[ToMock], withSettings().verboseLogging())
//    val external = CheckStackTrace(mocked)
//    external.executeAction()
//    verifyNoMoreInteractions(mocked)
//  }

}

object MultiDhtsTest {
  class NaggerDuckException() extends RuntimeException("Test exception so much")
  class ToMock {
    def printDate(): Unit = println(s"date: ${System.currentTimeMillis()}")
  }
  case class CheckStackTrace(toMock: ToMock) {
    def executeAction(): Unit = toMock.printDate()
  }
}