package com.realizationtime.btdogg.parsing

import java.nio.file.Path

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult.TorrentData

import scala.util.Try

case class ParsingResult(key: TKey, path: Path, result: Try[TorrentData]) {

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
