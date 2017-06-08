package protocol

import akka.util.ByteString

import scala.util.{Failure, Success, Try}

/**
  * Парсинг крайне не оптимальный, но в данном случае это вроде не принципиально.
  */
class Parser private (mappers: Map[Byte, CommandMapper[_ <: Command]]) {
  import Parser.Private._

  private val Empty = new Array[Byte](0)
  private var state: State.Normal = State.Normal.Code
  private var bytes: ByteString = _

  def parse(data: ByteString): Try[Vector[Command]] = {
    bytes = data
    val builder = Vector.newBuilder[Command]
    var continue = true
    var res: Try[Vector[Command]] = null
    while (continue) {
      val procRes = {
        import State.Normal._

        state match {
          case Code => processCode()
          case s: Mapper => processMapper(s)
          case s: Size => processSize(s)
          case s: Data => processData(s)
        }
      }
      procRes match {
        case newState: State.Normal =>
          state = newState

        case control: State.Control =>
          import State.Control._

          control match {
            case Result(command) =>
              builder += command
              state = State.Normal.Code
            case NeedData =>
              res = Success(builder.result())
              continue = false
            case Error(t) =>
              res = Failure(t)
              continue = false
          }
      }
    }
    res
  }

  private def processCode(): State = {
    if (bytes.isEmpty)
      State.Control.NeedData
    else {
      val code = bytes(0)
      State.Normal.Mapper(code)
    }
  }

  private def processMapper(s: State.Normal.Mapper): State = {
    mappers.get(s.code) match {
      case Some(mapper) => State.Normal.Size(mapper)
      case None => err(s"incorrect command with code = ${s.code}")
    }
  }

  private def processSize(s: State.Normal.Size): State = {
    if (bytes.size < 2)
      State.Control.NeedData
    else {
      val size = bytes(1)
      if (size < 0 || size > 125)
        err(s"incorrect size = $size")
      else {
        val data =
          if (size == 0) Empty
          else new Array[Byte](size)
        State.Normal.Data(s.mapper, data, 0)
      }
    }
  }

  private def processData(s: State.Normal.Data): State = {
    val bytesSize = bytes.size
    val dataSize = s.data.length
    var bytesPosition = 2
    while (s.position < dataSize && bytesPosition < bytesSize) {
      s.data(s.position) = bytes(bytesPosition)
      s.position += 1
      bytesPosition += 1
    }
    if (s.position >= dataSize) {
      s.mapper.read(s.data) match {
        case Success(command) =>
          bytes = bytes.slice(bytesPosition, bytesSize)
          State.Control.Result(command)
        case Failure(t) =>
          State.Control.Error(t)
      }
    } else
      State.Control.NeedData
  }

  private def err(msg: String): State.Control.Error =
    State.Control.Error(new RuntimeException(msg))
}

object Parser {

  def apply(): Parser = new Parser(mappers)

  private val mappers: Map[Byte, CommandMapper[_ <: Command]] =
    Mappers.list
      .map(mapper => mapper.code -> mapper)
      .toMap

  private object Private {

    sealed trait State

    object State {

      sealed trait Normal extends State
      object Normal {
        object Code extends Normal
        case class Mapper(code: Byte) extends Normal
        case class Size(mapper: CommandMapper[_ <: Command]) extends Normal
        case class Data(mapper: CommandMapper[_ <: Command], data: Array[Byte], var position: Int) extends Normal
      }

      sealed trait Control extends State
      object Control {
        case class Result(command: Command) extends Control
        object NeedData extends Control
        case class Error(t: Throwable) extends Control
      }
    }
  }
}
