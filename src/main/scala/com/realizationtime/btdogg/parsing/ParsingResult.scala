package com.realizationtime.btdogg.parsing

import java.nio.file.Path

import com.realizationtime.btdogg.TKey

import scala.util.{Failure, Success, Try}

case class ParsingResult[+T](key: TKey, path: Path, result: Try[T]) {
  def copyFailed[R](): ParsingResult[R] = {
    result match {
      case Failure(t) => ParsingResult[R](key, path, Failure(t))
      case _ => throw new IllegalArgumentException(s"copyFailed method available only for ParsingResults with failed result. $this")
    }
  }
  def copyTyped[R](newResult: Try[R]): ParsingResult[R] = ParsingResult(key, path, newResult)
  def copyTyped[R](newResult: R): ParsingResult[R] = ParsingResult(key, path, Success(newResult))
}

object ParsingResult {

  case class TorrentData(title: Option[String], totalSize: Long, files: List[FileEntry])

  object TorrentData {
    def singleFile(title: Option[String], totalSize: Long): TorrentData =
      TorrentData(title, totalSize, List(TorrentFile(title.get, totalSize)))
  }

  sealed abstract class FileEntry(val name: String)

  final case class TorrentFile(override val name: String, size: Long) extends FileEntry(name)

  final case class TorrentDir(override val name: String, contents: List[FileEntry]) extends FileEntry(name)

}
