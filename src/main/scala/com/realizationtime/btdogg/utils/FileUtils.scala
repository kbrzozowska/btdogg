package com.realizationtime.btdogg.utils

import java.nio.file.{Files, Path}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import com.realizationtime.btdogg.BtDoggConfiguration
import com.realizationtime.btdogg.parsing.ParsingResult
import com.typesafe.scalalogging.Logger

object FileUtils {

  private val doneTorrents: Path = BtDoggConfiguration.ScrapingConfig.torrentsTmpDir.resolve("done")
  private val faultyTorrents = BtDoggConfiguration.ScrapingConfig.torrentsTmpDir.resolve("faulty")
  Files.createDirectories(doneTorrents)
  Files.createDirectories(faultyTorrents)
  private val log = Logger(FileUtils.getClass)

  def moveFileTo(file: Path, targetDir: Path): Path = {
    val filename = file.getFileName
    val newLocation = targetDir.resolve(filename)
    Files.move(file, newLocation, REPLACE_EXISTING)
    newLocation
  }

  def moveFileToFaulty(file: Path): Path = moveFileTo(file, faultyTorrents)
  def moveFileToDone(file: Path): Path = moveFileTo(file, FileUtils.doneTorrents)

  def removeFile[T](res: ParsingResult[T]): ParsingResult[T] = {
    try {
      Files.delete(res.path)
    } catch {
      case ex: Throwable =>
        log.error(s"error deleting torrent file ${res.path}", ex)
    }
    res
  }

}
