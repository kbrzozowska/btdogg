package com.realizationtime.btdogg.parsing

import java.nio.file.{Files, Paths}

import com.realizationtime.btdogg.commons.{ParsingResult, TKey, TorrentData}
import org.scalatest.Inside.inside
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class FileParserTest extends FlatSpec with Matchers {

  "FileParser" should "parse file" in {
    val testFile = Paths.get("src/test/resources/test.torrent")
    require(Files.isRegularFile(testFile))
    val parsingResult = FileParser.parse(TKey.fromPrefix(42), testFile)
    inside(parsingResult) {
      case ParsingResult(_, _, res) =>
        res should not be a[Failure[_]]
      case _ =>
    }
    //    println(parsingResult)
  }

  it should "parse torrent containing one file" in {
    val testFile = Paths.get("src/test/resources/singleFile.torrent")
    require(Files.isRegularFile(testFile))
    val parsingResult = FileParser.parse(TKey.fromPrefix(42), testFile)
    inside(parsingResult) {
      case ParsingResult(_, _, res) =>
        res should not be a[Failure[_]]
        inside(res) {
          case Success(TorrentData(_, _, files)) =>
            files should have size 1
        }
    }
  }

}
