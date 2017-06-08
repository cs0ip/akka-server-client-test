package server.app

import server.actors.Server
import akka.actor.ActorSystem
import org.slf4j.LoggerFactory
import resource._
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcBackend

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}


object Main extends App {
  val log = LoggerFactory.getLogger(getClass)
  for {
    db <- managed(Database.forURL("jdbc:h2:file:./db", user = "sa"))
  } {
    Await.ready(Dao.setup(db)(db.executor.executionContext), Duration.Inf).value.get match {
      case Success(_) => withDb(db)
      case Failure(t) => log.error("error when creating db tables", t)
    }
  }

  private def withDb(db: JdbcBackend#DatabaseDef): Unit = {
    val system = ActorSystem("system")
    system.actorOf(Server.props(db))
    val termFuture = system.whenTerminated
    Await.ready(termFuture, Duration.Inf)
    println(s"system termination result = ${termFuture.value}")
  }
}
