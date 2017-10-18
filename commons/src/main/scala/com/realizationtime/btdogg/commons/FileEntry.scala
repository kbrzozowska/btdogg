package com.realizationtime.btdogg.commons

sealed abstract class FileEntry

object FileEntry {

  final case class TorrentFile(name: String, size: Long) extends FileEntry

  final case class TorrentDir(name: String, contents: List[FileEntry]) extends FileEntry

}