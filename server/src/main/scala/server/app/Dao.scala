package server.app

import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcBackend
import slick.jdbc.meta.MTable

import scala.concurrent.{ExecutionContext, Future}

object Dao {
  class Session(tag: Tag) extends Table[(String, String, String, Int)](tag, "session") {
    def id = column[String]("id", O.PrimaryKey)
    def packs = column[String]("packs")
    def state = column[String]("state", O.Default(Session.State.Uncompleted))
    def prize = column[Int]("prize", O.Default(0))

    override def * = (id, packs, state, prize)
  }

  object Session {
    object State {
      val Uncompleted = "uncompleted"
      val Completed = "completed"
    }
  }

  val sessions = TableQuery[Session]


  class Step(tag: Tag) extends Table[(String, Int, String, Option[Boolean])](tag, "step") {
    def sessionId = column[String]("session_id")
    def step = column[Int]("step")
    def packNums = column[String]("pack_nums")
    def yes = column[Option[Boolean]]("yes", O.Default(None))

    def session = foreignKey("step_fk_session_id", sessionId, sessions)(
      _.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Cascade
    )

    override def * = (sessionId, step, packNums, yes)
  }

  val steps = TableQuery[Step]


  val tables = Vector(sessions, steps)

  def setup(db: JdbcBackend#DatabaseDef)(implicit ec: ExecutionContext): Future[Vector[Unit]] = {
    val existing = db.run(MTable.getTables)
    val future = existing.flatMap(v => {
      val names = v.map(mt => mt.name.name)
      val createIfNotExist = tables
        .filter(table => !names.contains(table.baseTableRow.tableName))
        .map(_.schema.create)
      db.run(DBIO.sequence(createIfNotExist))
    })
    future
  }
}
