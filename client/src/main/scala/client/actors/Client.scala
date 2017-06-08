package client.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.io.{IO, Tcp}
import akka.io.Tcp._
import protocol.{Command, Parser, Writer}
import utils.BufferedWrite

import scala.util.{Failure, Success}

class Client(remote: InetSocketAddress) extends Actor with ActorLogging with BufferedWrite {
  import context.system

  private val handler = context.actorOf(Handler.props(self), "handler")
  context watch handler

  private val parser = Parser()
  private var connection: ActorRef = _

  IO(Tcp) ! Connect(remote)

  override def receive: Receive = {
    case CommandFailed(_: Connect) =>
      log.error("connection failed")
      context stop self

    case Connected(_, local) =>
      connection = sender()
      connection ! Register(self)
      context watch connection
      log.info(s"client connected: $local -> $remote")
      context become connected
      handler ! Handler.Start
  }

  override def postStop(): Unit = {
    context.system.terminate()
  }

  private def connected: Receive = {
    case CommandFailed(c: Write) =>
      log.error(s"write failed; c = $c")
      context stop self

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
      context.system.terminate()
      context stop self

    case unexpected =>
      log.error(s"Unexpected message: $unexpected")
  }
}

object Client {
  def props(remote: InetSocketAddress): Props = Props(classOf[Client], remote)

  object Fin
}
