package com.realizationtime.btdogg.commons

sealed abstract class FileEntry(val name: String)

object FileEntry {

  final case class TorrentFile(override val name: String, size: Long) extends FileEntry(name)

  final case class TorrentDir(override val name: String, contents: List[FileEntry]) extends FileEntry(name)

}