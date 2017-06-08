package client.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import protocol.Command

import scala.io.Source
import scala.util.{Failure, Success, Try}

class Handler(client: ActorRef)  extends Actor with ActorLogging {
  import Handler.Private._

  private var packs: Vector[Int] = Vector.empty

  override def receive: Receive = {
    case Handler.Start =>
      log.debug("handler started")
      client ! Command.Login
      context become waitLoggedIn

    case unexpected =>
      log.error(s"Unexpected message: $unexpected")
  }

  private def waitLoggedIn: Receive = {
    case Command.LoggedIn(newPacks) =>
      packs = newPacks
      log.debug(s"logged in: packs = $packs")
      context become handleNext

    case unexpected =>
      log.error(s"Unexpected message: $unexpected")
      context stop self
  }

  private def handleNext: Receive = {
    case Command.Next(packNums) =>
      getPacksByNums(packNums) match {
        case Success(nextPacks) =>
          val sum = nextPacks.foldLeft(0)((sum, pack) => sum + pack.value)
          println(s"offer = $nextPacks; sum = $sum")
          val yes = readUserResponse()
          client ! Command.ClientResponse(yes)
        case Failure(t) =>
          log.error(t, "incorrect server response")
          context stop self
      }

    case Command.Prize(prizePacks) =>
      getPacksByNums(prizePacks) match {
        case Success(lastPacks) =>
          val sum = lastPacks.foldLeft(0)((sum, pack) => sum + pack.value)
          println(s"prize = $lastPacks; sum = $sum")
          client ! Client.Fin
        case Failure(t) =>
          log.error(t, "incorrect server response")
          context stop self
      }

    case unexpected =>
      log.error(s"Unexpected message: $unexpected")
      context stop self
  }

  private def readUserResponse(): Boolean = {
    var continue = true
    var res = false
    while (continue) {
      println("enter response (y or n): ")
      Source.stdin.getLines().map(_.trim).find(_.nonEmpty) match {
        case Some(str) =>
          str.charAt(0) match {
            case 'y' =>
              res = true
              continue = false
              println("response: yes")
            case 'n' =>
              res = false
              continue = false
              println("response: no")
            case _ =>
              println("response: incorrect")
          }

        case None => throw new Exception("not a number")
      }
    }
    res
  }

  private def getPacksByNums(packNums: Vector[Int]): Try[Vector[Pack]] = {
    val builder = Vector.newBuilder[Pack]
    if (packNums.size < 1)
      return Failure(new Exception("server response is empty"))
    for (num <- packNums) {
      if (num >= 0 && num < packs.size) {
        val value = packs(num)
        builder += Pack(num, value)
      } else
        return Failure(new Exception(s"incorrect pack index = $num"))
    }
    Success(builder.result())
  }
}

object Handler {
  def props(client: ActorRef): Props = Props(classOf[Handler], client)

  object Start

  private object Private {
    case class Pack(index: Int, value: Int)
  }
}
