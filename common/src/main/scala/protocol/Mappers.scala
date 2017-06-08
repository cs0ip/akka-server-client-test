package protocol

import protocol.Command._

object Mappers {
  val list = List(
    new LoginMapper,
    new LoggedInMapper,
    new NextMapper,
    new ClientResponseMapper,
    new PrizeMapper,
    new ErrorMapper
  )
}
