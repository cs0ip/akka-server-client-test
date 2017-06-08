package client.app

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import client.actors.Client

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  val system = ActorSystem("system")
  val client = system.actorOf(Client.props(new InetSocketAddress("localhost", 8090)))
  Await.result(system.whenTerminated, Duration.Inf)
  println("end")
}
