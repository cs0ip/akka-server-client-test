package protocol

import java.nio.charset.StandardCharsets

import akka.util.{ByteString, ByteStringBuilder}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

sealed trait Command

sealed abstract class CommandMapper[T <: Command : ClassTag] {
  val commandClass: Class[T] = {
    val tag = scala.reflect.classTag[T]
    tag.runtimeClass.asInstanceOf[Class[T]]
  }

  def code: Byte

  def read(data: Array[Byte]): Try[T]

  def write(command: T): Try[ByteString] = {
    val builder = ByteString.newBuilder
    builder += code
    for (_ <- writeToBuilder(builder, command))
      yield builder.result()
  }

  protected def writeToBuilder(b: ByteStringBuilder, command: T): Try[Unit] = {
    b += 0 //size
    Success(Unit)
  }
}

object Command {
  object Login extends Command

  class LoginMapper extends CommandMapper[Login.type] {
    val code: Byte = 1

    def read(data: Array[Byte]): Try[Login.type] = Success(Login)
  }


  case class LoggedIn(packs: Vector[Int]) extends Command

  class LoggedInMapper extends CommandMapper[LoggedIn] with PackMapper {
    val code: Byte = 2

    override def read(data: Array[Byte]): Try[LoggedIn] =
      for (packs <- readPacks(data))
        yield LoggedIn(packs)

    override protected def writeToBuilder(b: ByteStringBuilder, command: LoggedIn): Try[Unit] =
      writePacks(b, command.packs)

    override protected def err(msg: String): Failure[Nothing] =
      Failure(new RuntimeException(s"LoggedIn command: $msg"))

    override protected def max: Int = 30
  }


  case class Next(packs: Vector[Int]) extends Command

  class NextMapper extends CommandMapper[Next] with PackMapper {
    val code: Byte = 3

    override def read(data: Array[Byte]): Try[Next] =
      for (packs <- readPacks(data))
        yield Next(packs)

    override protected def writeToBuilder(b: ByteStringBuilder, command: Next): Try[Unit] =
      writePacks(b, command.packs)

    override protected def err(msg: String): Failure[Nothing] =
      Failure(new RuntimeException(s"Next command: $msg"))

    override protected def max: Int = 3
  }


  case class ClientResponse(yes: Boolean) extends Command

  class ClientResponseMapper extends CommandMapper[ClientResponse] {
    val code: Byte = 4

    override def read(data: Array[Byte]): Try[ClientResponse] = {
      val size = data.length
      if (size != 1)
        return err(s"incorrect size = $size")
      data(0) match {
        case 0 => Success(ClientResponse(false))
        case 1 => Success(ClientResponse(true))
        case x => err(s"incorrect value = $x")
      }
    }

    override protected def writeToBuilder(b: ByteStringBuilder, command: ClientResponse): Try[Unit] = {
      val value: Byte = if (command.yes) 1 else 0
      b += 1 //size
      b += value
      Success(Unit)
    }

    private def err(msg: String): Failure[Nothing] =
      Failure(new RuntimeException(s"ClientResponse command: $msg"))
  }


  case class Prize(packs: Vector[Int]) extends Command

  class PrizeMapper extends CommandMapper[Prize] with PackMapper {
    val code: Byte = 5

    override def read(data: Array[Byte]): Try[Prize] =
      for (packs <- readPacks(data))
        yield Prize(packs)

    override protected def max: Int = 3

    override protected def err(msg: String): Failure[Nothing] =
      Failure(new RuntimeException(s"Prize command: $msg"))

    override protected def writeToBuilder(b: ByteStringBuilder, command: Prize): Try[Unit] =
      writePacks(b, command.packs)
  }


  case class Error(str: String) extends Command

  class ErrorMapper extends CommandMapper[Error] {
    override def code: Byte = 0

    override def read(data: Array[Byte]): Try[Error] =
      for (msg <- Try(new String(data, StandardCharsets.UTF_8)))
        yield Error(msg)

    override protected def writeToBuilder(b: ByteStringBuilder, command: Error): Try[Unit] = {
      val bytes = command.str.getBytes(StandardCharsets.UTF_8)
      val size = bytes.length
      if (size > 125)
        return err(s"size is too big; size = $size")
      b += size.asInstanceOf[Byte]
      b.putBytes(bytes)
      Success(Unit)
    }

    private def err(msg: String): Failure[Nothing] =
      Failure(new RuntimeException(s"Error command: $msg"))
  }


  trait PackMapper { this: CommandMapper[_] =>
    protected def max: Int

    protected def readPacks(data: Array[Byte]): Try[Vector[Int]] = {
      val size = data.length
      if (size < 1 || size % 4 != 0)
        return err(s"incorrect read size = $size")
      val cnt = size / 4
      if (cnt > max)
        return err(s"too many element; cnt = $cnt")
      val builder = Vector.newBuilder[Int]
      for (i <- 0 until size by 4) {
        var pack = 0
        for (j <- 0 until 4)
          pack = (pack << 8) | (data(i + j) & 0xff)
        if (pack < 0)
          return err(s"pack with index $i < 0; pack = $pack")
        builder += pack
      }
      Success(builder.result())
    }

    protected def writePacks(b: ByteStringBuilder, packs: Vector[Int]): Try[Unit] = {
      val packCount = packs.size
      if (packCount < 1 || packCount > max)
        return err(s"incorrect pack count = $packCount")
      val size = (packCount * 4).asInstanceOf[Byte]
      b += size
      for (pack <- packs) {
        for (i <- 3 to 0 by -1) {
          val byte = (pack >>> (i * 8)).asInstanceOf[Byte]
          b += byte
        }
      }
      Success(Unit)
    }

    protected def err(msg: String): Failure[Nothing]
  }
}
