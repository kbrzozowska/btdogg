package com.realizationtime.btdogg.commons

import java.nio.file.Path

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
