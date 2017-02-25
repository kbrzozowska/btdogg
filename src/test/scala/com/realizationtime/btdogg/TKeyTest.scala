package com.realizationtime.btdogg

import org.scalatest.Inside.inside
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class TKeyTest extends FlatSpec with Matchers with PropertyChecks {

  "A TKey" should "accept correct torrent hash" in {
    TKey("0123456789ABCDEF0123456789ABCDEF01234567")
    TKey("1C768608AF709048A71446035A326F5522B59B35")
  }

  it should "throw on null" in {
    an[IllegalArgumentException] should be thrownBy {
      TKey(null.asInstanceOf[String])
    }
  }

  it should "throw on too short input" in {
    an[IllegalArgumentException] should be thrownBy {
      TKey("0123ABC")
    }
    an[IllegalArgumentException] should be thrownBy {
      TKey("0123456789ABCDEF0123456789ABCDEF0123456") // len=39
    }
  }

  it should "throw on characters other than legal hex nibbles in input" in {
    an[IllegalArgumentException] should be thrownBy {
      TKey("01234567../BCDEF0123456789ABCDEF01234567")
    }
  }

  "TKey.fromPrefix" should "produce TKey on correct input" in {
    val key: TKey = TKey.fromPrefix("A")
    key should not be null
  }

  it should "generate TKey which hash has given prefix" in {
    val key: TKey = TKey.fromPrefix("ABCDE")
    inside(key) { case TKey(hash) =>
      hash should startWith("ABCDE")
    }
  }

  "TKey.fromPrefix(Int)" should "throw on too big input" in {
    an[IllegalArgumentException] should be thrownBy {
      TKey.fromPrefix(256)
    }
  }

  it should "throw on input < 0" in {
    an[IllegalArgumentException] should be thrownBy {
      TKey.fromPrefix(-1)
    }
  }

  it should "return correct results for correct input" in {
    forAll(Table(
      ("intPrefix", "stringPrefixInResult"),
      (0, "00"),
      (5, "05"),
      (15, "0F"),
      (17, "11"),
      (64, "40"),
      (130, "82"),
      (255, "FF")
    )) { (n, result) =>
      val key = TKey.fromPrefix(n)
      inside(key) { case TKey(hash) =>
        hash should startWith(result)
      }
    }
  }
}
