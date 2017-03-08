package com.realizationtime.btdogg.parsing

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util

import com.realizationtime.btdogg.TKey
import com.realizationtime.btdogg.parsing.ParsingResult.{FileEntry, TorrentData, TorrentDir, TorrentFile}
import the8472.mldht.cli.TorrentInfo

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters
import scala.util.{Failure, Success}

object FileParser {
  def parse(key: TKey, path: Path): ParsingResult = {
    try {
      val ti: TorrentInfo = new TorrentInfo(path)
      ti.decode()
      val name = OptionConverters.toScala(ti.name())
      val totalSize: Long = ti.totalSize()

      val flatFiles: List[FlatFile] = ti.files().asScala.toList.asInstanceOf[List[java.util.Map[String, Any]]]
        .map(_.asScala.toMap)
        .map(FlatFile(_))
        .filter(_.isDefined)
        .map(_.get)
      if (flatFiles.isEmpty) {
        if (name.isDefined)
          ParsingResult(key, path, Success(TorrentData.singleFile(name, totalSize)))
        else
          ParsingResult(key, path, Failure(NoFilesFound(key)))
      } else {
        val treeFiles: List[FileEntry] = flatToTree(flatFiles)
        ParsingResult(key, path, Success(TorrentData(name, totalSize, treeFiles)))
      }
    } catch {
      case ex: Throwable => ParsingResult(key, path, Failure(ex))
    }
  }

  private case class FlatFile(path: List[String], size: Long) {
    lazy val dropHead: FlatFile = FlatFile(path.tail, size)
  }

  private object FlatFile {
    def apply(fileInfo: Map[String, Any]): Option[FlatFile] = {
      val pathBytes: List[Array[Byte]] = fileInfo.get("path.utf-8").orElse(fileInfo.get("path"))
        .map(_.asInstanceOf[util.List[Array[Byte]]].asScala.toList)
        .getOrElse(List())
      if (pathBytes.isEmpty)
        None
      else {
        val path: List[String] = pathBytes.map(new String(_, StandardCharsets.UTF_8))
        val size = fileInfo.get("length").map(_.asInstanceOf[Long]).getOrElse(0L)
        Some(FlatFile(path, size))
      }
    }
  }

  case class NoFilesFound(key: TKey) extends RuntimeException(s"No files found in torrent: $key")

  def flatToTree(flatFiles: List[FlatFile]): List[FileEntry] = {
    val classed = flatFiles.groupBy(f => {
      if (f.path.length == 1)
        classOf[TorrentFile]
      else
        classOf[TorrentDir]
    }).withDefaultValue(List())
    val files: List[TorrentFile] = classed(classOf[TorrentFile]).map(flat => TorrentFile(flat.path.head, flat.size)).sortBy(_.name)
    val dirs = classed(classOf[TorrentDir])
    val dirsTree: List[TorrentDir] = dirs.groupBy(_.path.head)
      .mapValues(_.map(_.dropHead))
      .toList
      .sortBy(_._1)
      .map { case (dirName, dirContent) =>
        TorrentDir(dirName, flatToTree(dirContent))
      }
    files ++ dirsTree
  }
}