package server.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.io.Tcp.{ConfirmedClose, ConnectionClosed, Received}
import protocol.{Command, Parser, Writer}
import slick.jdbc.JdbcBackend
import utils.BufferedWrite

import scala.util.{Failure, Success}

class Client(addr: InetSocketAddress, connection: ActorRef, db: JdbcBackend#DatabaseDef)
  extends Actor
  with ActorLogging
  with BufferedWrite
{
  private val id = UUID.randomUUID().toString
  private val handler = context.actorOf(Handler.props(id, self, db), s"handler-$id")
  private val parser = Parser()

  log.debug(s"client[$addr] -> handler[$id]")

  context watch connection
  context watch handler

  override def receive: Receive = {
    case Received(data) =>
      parser.parse(data) match {
        case Success(commands) =>
          commands.foreach(command => handler ! command)
        case Failure(t) =>
          log.error(t, "error while parsing command")
          context stop self
      }

    case c: Command =>
      Writer.toByteString(c) match {
        case Success(byteString) =>
          buffer(connection, byteString)
        case Failure(t) =>
          log.error(t, "error when serializing command")
          context stop self
      }

    case BufferedWrite.Ack =>
      acknowledge(connection)

    case c: ConnectionClosed =>
      log.info(s"Connection closed by ConnectionClosed message: $c")
      context stop self

    case t: Terminated =>
      log.info(s"Connection closed by Terminated message: $t")
      context stop self

    case Client.Fin =>
      log.info("Game finished")
      connection ! ConfirmedClose

    case unexpected =>
      log.error(s"Unexpected message: $unexpected")
  }
}

object Client {
  def props(addr: InetSocketAddress, connection: ActorRef, db: JdbcBackend#DatabaseDef): Props =
    Props(classOf[Client], addr, connection, db)

  case class Error(msg: String)

  object Fin
}
