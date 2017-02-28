package com.realizationtime.btdogg

import lbms.plugins.mldht.kad.Key

import scala.language.postfixOps
import scala.util.Random

case class TKey(hash: String) {

  require(hash != null && hash.length == 40 && hash.matches(TKey.validHex), {
    s"hash should be haxadecimal, with size 40. You passed: $hash"
  })

  lazy val mldhtKey: Key = new Key(hash)
  lazy val prefix: String = hash.take(2)
}

object TKey {

  private val hashLength = Key.SHA1_HASH_LENGTH * 2
  private val validHex = """^[0-9A-F]{hashLength}$""".replace("hashLength", hashLength.toString)

  def apply(key: Key): TKey = {
    val uglyPrint = false
    new TKey(key.toString(uglyPrint))
  }

  private val nibbles: Vector[Char] = ('0' to '9') ++ ('A' to 'F') toVector

  def fromPrefix(hashPrefix: String): TKey = {
    require(hashPrefix != null && hashPrefix.length <= 40)
    val postfix = Iterator.continually({
      Random.nextInt(nibbles.length)
    })
      .map(nibbles(_))
      .take(hashLength - hashPrefix.length)
    TKey(hashPrefix + postfix.mkString)
  }

  def fromPrefix(unsignedByte: Int): TKey = {
    require(unsignedByte < 256)
    require(unsignedByte >= 0)
    fromPrefix("" + nibbles(unsignedByte / nibbles.length) + nibbles(unsignedByte % nibbles.length))
  }

}