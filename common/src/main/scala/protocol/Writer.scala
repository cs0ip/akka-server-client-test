package protocol

import akka.util.ByteString

import scala.util.{Failure, Try}

object Writer {
  def toByteString[T <: Command](command: T): Try[ByteString] = {
    val cls = command.getClass.asInstanceOf[Class[T]]
    mappers.get(cls) match {
      case Some(mapper) =>
        val typedMapper = mapper.asInstanceOf[CommandMapper[T]]
        typedMapper.write(command)
      case None =>
        Failure(new RuntimeException(s"unsupported command class = $cls"))
    }
  }

  private val mappers: Map[Class[_ <: Command], CommandMapper[_ <: Command]] =
    Mappers.list
      .map(mapper => mapper.commandClass -> mapper)
      .toMap
}
