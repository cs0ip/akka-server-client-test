package utils

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Tcp.{Event, ResumeReading, SuspendReading, Write}
import akka.util.ByteString

import scala.collection.mutable

trait BufferedWrite { this: Actor with ActorLogging =>

  import BufferedWrite._

  private val highWatermark = 128 * 1024
  private val lowWatermark = 32 * 1024
  private val maxStored = 1024 * 1024

  private val storage = mutable.Queue.empty[ByteString]
  private var stored = 0
  private var suspended = false

  protected def buffer(connection: ActorRef, data: ByteString): Unit = {
    storage.enqueue(data)
    stored += data.size

    if (stored > highWatermark) {
      connection ! SuspendReading
      suspended = true
    }

    if (stored > maxStored)
      log.warning(s"buffer overrun")

    if (storage.size == 1)
      connection ! Write(data, Ack)
  }

  protected def acknowledge(connection: ActorRef): Unit = {
    val data = storage.dequeue()
    stored -= data.size

    log.debug(s"after ack: stored = $stored")
    if (suspended && stored < lowWatermark) {
      connection ! ResumeReading
      suspended = false
    }

    if (storage.nonEmpty)
      connection ! Write(data, Ack)
  }
}

object BufferedWrite {
  object Ack extends Event
}
