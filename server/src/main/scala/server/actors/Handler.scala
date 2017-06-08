package server.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import server.app.Dao
import protocol.Command
import protocol.Command._
import slick.jdbc.JdbcBackend
import slick.jdbc.H2Profile.api._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Random, Success}

import scala.concurrent.ExecutionContext.Implicits.global

class Handler(id: String, client: ActorRef, db: JdbcBackend#DatabaseDef) extends Actor with ActorLogging {
  import Handler._

  private var packs: Vector[Int] = Vector.empty
  private var packNums: Vector[Int] = Vector.empty
  private var step = 0
  private var fin = false

  override def receive: Receive = {
    case Login =>
      packs = Handler.packs
      client ! LoggedIn(packs)
      val next = generateNext
      val commands = List(next)
      log.debug(s"client[$id]: logged in")
      db.run(DBIO.seq(
        Dao.sessions.map(s => (s.id, s.packs)) += ((id, packs.mkString(","))),
        stepToDb(next)
      ).transactionally).onComplete {
        case Success(_) => self ! DbDone(commands)
        case Failure(t) => self ! DbError(t)
      }

    case DbDone(commands) =>
      sendCommands(commands)
      context become loggedIn

    case DbError(t) =>
      log.error(t, "db error")
      context stop self

    case unexpected =>
      log.error(s"unexpected command = $unexpected")
      context stop self
  }

  private def loggedIn: Receive = {
    case ClientResponse(yes) =>
      if (fin) {
        log.error("game finished")
        context stop self
      } else {
        val queries = ArrayBuffer.empty[DBIO[_]]
        val commads = ArrayBuffer.empty[Command]
        queries += {
          val prevStep = step
          setStepYes(prevStep, yes)
        }
        def addPrize(): Unit = {
          val sum =  packNums.map(ind => packs(ind)).sum
          queries += completeSession(sum)
          commads += Prize(packNums)
          fin = true
        }
        if (yes)
          addPrize()
        else {
          val next = generateNext
          queries += stepToDb(next)
          if (step >= MaxStep)
            addPrize()
          else
            commads += next
        }
        val unionQuery = queries.reduce((x, y) => x andThen y)
        db.run(DBIO.seq(unionQuery).transactionally) onComplete {
          case Success(_) => self ! DbDone(commads)
          case Failure(t) => self ! DbError(t)
        }
      }

    case DbDone(commands) =>
      sendCommands(commands)
      if (fin)
        client ! Client.Fin

    case DbError(t) =>
      log.error(t, "db error")
      context stop self

    case unexpected =>
      log.error(s"unexpected command = $unexpected")
      context stop self
  }

  private def stepToDb(next: Next): DBIO[Int] =
    Dao.steps.map(s => (s.sessionId, s.step, s.packNums)) += ((id, step, next.packs.mkString(",")))

  private def setStepYes(step: Int, yes: Boolean): DBIO[Int] =
    Dao.steps
      .filter(s => s.sessionId === id && s.step === step)
      .map(s => s.yes)
      .update(Some(yes))

  private def sendCommands(commands: Seq[Command]): Unit = {
    for (cmd <- commands) {
      client ! cmd
      log.debug(s"client[$id] send: $cmd")
    }
  }

  private def completeSession(prize: Int): DBIO[Int] =
    Dao.sessions
      .filter(s => s.id === id)
      .map(s => (s.state, s.prize))
      .update((Dao.Session.State.Completed, prize))

  //не самый эффективный способ
  private def generateNext: Next = {
    val cnt = Math.min(Random.nextInt(3) + 1, packs.size)
    val set = mutable.HashSet.empty[Int]
    while (set.size < cnt)
      set += Random.nextInt(packs.size)
    val nextPackNums = set.toSeq.sorted.toVector
    step += 1
    packNums = nextPackNums
    Next(packNums)
  }
}

object Handler {
  def props(id: String, client: ActorRef, db: JdbcBackend#DatabaseDef): Props =
    Props(classOf[Handler], id, client, db)

  //вместо чтения из базы или конфига
  val packs = Vector(25, 25, 50, 50, 100, 250, 500, 1000, 5000)

  val MaxStep = 4

  case class DbDone(commands: Seq[Command])

  case class DbError(t: Throwable)
}
