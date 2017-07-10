package com.realizationtime.btdogg.persist

import java.nio.file.Paths
import java.time.LocalDate

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.{FileParser, ParsingResult}
import com.realizationtime.btdogg.persist.MongoPersist.TorrentDocument
import org.scalatest.Inside.inside
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import reactivemongo.api.commands.UpdateWriteResult

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Success, Try}

class MongoPersistImplTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val mongoPersist: MongoPersist = new MongoPersistImpl("mongodb://localhost/test")
  private val testKey = TKey("6DBE929E7579CADD7E66F37ACCC5B16DE6A4BFF7")
  private val testFile = Paths.get("src/test/resources/6DBE929E7579CADD7E66F37ACCC5B16DE6A4BFF7.torrent")
  private val parsingResult = FileParser.parse(testKey, testFile)

  val tenSeconds = FiniteDuration(10, "s")

  "mongoPersist" should "be able to save" in {
    val saveFuture = saveTorrent()
    assertInsertSucceeded(blockOnFuture(saveFuture))
  }

  def blockOnFuture[T](f: Future[T]): Try[T] = {
    val p = Promise[Try[T]]()
    f.onComplete(res => p.complete(Success(res)))
    Await.result(p.future, tenSeconds)
  }

  it should "ignore double saves silently" in {
    val f1 = saveTorrent()
    assertInsertSucceeded(blockOnFuture(f1))
    val f2 = saveTorrent()
    assertInsertSucceeded(blockOnFuture(f2))
  }

  it should "increment liveness counters correctly" in {
    val saveFuture = saveTorrent()
    assertInsertSucceeded(blockOnFuture(saveFuture))
    val date = LocalDate.now()
    val incrementFuture = mongoPersist.incrementLiveness(testKey, date, 1, 0)
    val res: Try[UpdateWriteResult] = blockOnFuture(incrementFuture)
    res shouldBe a[Success[_]]
  }

  it should "not increment liveness counters for non-existing torrent" in {
    val saveFuture = saveTorrent()
    assertInsertSucceeded(blockOnFuture(saveFuture))
    val date = LocalDate.now()
    val nonExisting = TKey("1234567890ABCDEF1234567890ABCDEF12345678")
    val incrementFuture = mongoPersist.incrementLiveness(nonExisting, date, 1, 2)
    val res: Try[UpdateWriteResult] = blockOnFuture(incrementFuture)
    res shouldBe a[Success[_]]
  }

  def saveTorrent(): Future[ParsingResult[TorrentDocument]] = {
    mongoPersist.save(parsingResult)
  }

  private def assertInsertSucceeded(resTry: Try[ParsingResult[TorrentDocument]]) = {
    resTry shouldBe a[Success[_]]
    val res = resTry.get
    inside(res) {
      case ParsingResult(_, _, dataTry) =>
        dataTry shouldBe a[Success[_]]
    }
  }

  override protected def afterEach(): Unit = {
    Await.ready(mongoPersist.delete(testKey), tenSeconds)
  }
}
