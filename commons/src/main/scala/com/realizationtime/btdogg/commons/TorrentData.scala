package com.realizationtime.btdogg.commons

import com.realizationtime.btdogg.commons.FileEntry.TorrentFile

case class TorrentData(title: Option[String], totalSize: Long, files: List[FileEntry])

object TorrentData {
  def singleFile(title: Option[String], totalSize: Long): TorrentData =
    TorrentData(title, totalSize, List(TorrentFile(title.get, totalSize)))
}
