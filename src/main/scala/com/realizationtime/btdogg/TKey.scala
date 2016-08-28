package com.realizationtime.btdogg

import lbms.plugins.mldht.kad.Key

case class TKey(hash: String) {

  if (hash == null || hash.length != 40)
    throw new IllegalArgumentException(s"hash should be haxadecimal, with size 40. You passed: $hash")

}

object TKey{

  def apply(key: Key): TKey = {
    val uglyPrint = false
    new TKey(key.toString(uglyPrint))
  }

}