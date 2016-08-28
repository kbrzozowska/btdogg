package com.realizationtime.btdogg

import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING}
import java.nio.file.{Files, Path, Paths}
import java.util

import lbms.plugins.mldht.DHTConfiguration
import lbms.plugins.mldht.kad.DHT
import lbms.plugins.mldht.kad.DHT.IncomingMessageListener
import lbms.plugins.mldht.kad.messages.MessageBase
import lbms.plugins.mldht.kad.messages.MessageBase.Method.{ANNOUNCE_PEER, GET, GET_PEERS}
import lbms.plugins.mldht.kad.tasks.PeerLookupTask
import org.scalatest.FlatSpec

import scala.annotation.tailrec

/**
  * Playing around with library
  */
class IntegratingMldht extends FlatSpec {

  "DHT" should "run" ignore {
    val dht = new DHT(DHT.DHTtype.IPV4_DHT)
    dht.start(new DHTConfiguration {
      override def getListeningPort: Int = 24999

      override def allowMultiHoming(): Boolean = false

      override def isPersistingID: Boolean = true

      override def getStoragePath: Path = Paths.get("/tmp/mldht")

      override def noRouterBootstrap(): Boolean = false
    })
    dht.bootstrap()
    dht.addIncomingMessageListener(new IncomingMessageListener {
      override def received(dht: DHT, msg: MessageBase): Unit = {
        val akceptowalne = util.EnumSet.of(ANNOUNCE_PEER, GET_PEERS, GET)
        if (akceptowalne.contains(msg.getMethod)) {
          val log = "przyszedł message: " + msg.getType + " " + msg.getMethod + " " + msg.getInnerMap
          Files.write(Paths.get("/tmp/mldhtMessages"), (log + "\n").getBytes, TRUNCATE_EXISTING, CREATE)
          println(log)
        }
      }
    })
    val hash = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    println("is running? " + dht.isRunning)
    @tailrec
    def newTask: PeerLookupTask = {
      val ret = dht.createPeerLookup(hash)
      if (ret != null)
        ret
      else {
        Thread.sleep(100)
        println("GOT NULL!" + dht.isRunning)
        dht.getOurID
        newTask
      }
    }
    val task: PeerLookupTask = newTask

    dht.getTaskManager.addTask(task)

    while (true) {
      Thread.sleep(200)
    }
  }

}
