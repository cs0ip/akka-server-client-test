package server.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.{IO, Tcp}
import slick.jdbc.JdbcBackend

class Server(db: JdbcBackend#DatabaseDef) extends Actor with ActorLogging {
  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress(8090))

  override def receive: Receive = {
    case Bound(_: InetSocketAddress) =>
      context.become(bounded)
      log.debug("server started")

    case CommandFailed(b: Bind) =>
      log.error(s"Bind failed; Bind = $b")
      context stop self
  }

  def bounded: Receive = {
    case Connected(clientAddr, _) =>
      val connection = sender()
      val client = context.actorOf(
        Client.props(clientAddr, connection, db),
        s"client-${clientAddr.getHostName}:${clientAddr.getPort}"
      )
      connection ! Register(client)
      log.debug(s"client connected; addr = $clientAddr")
  }
}

object Server {
  def props(db: JdbcBackend#DatabaseDef): Props =
    Props(classOf[Server], db)
}
