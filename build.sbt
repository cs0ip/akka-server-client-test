import sbt.Keys.scalaVersion

lazy val commonSettings = Seq(
  version := "1.0.0",
  scalaVersion := "2.12.2",
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-deprecation",
    "-feature",
    "-Xfatal-warnings",
    "-Xlint"
  )
)

val dep = new AnyRef {
  val slick = "com.typesafe.slick" %% "slick" % "3.2.0"
  val hikaricp = "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0"
  val h2 = "com.h2database" % "h2" % "1.4.195"
  val log4jSlf4jImpl = "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.2"
  val log4jApi = "org.apache.logging.log4j" % "log4j-api" % "2.8.2"
  val log4jCore = "org.apache.logging.log4j" % "log4j-core" % "2.8.2"
  val scalaArm = "com.jsuereth" %% "scala-arm" % "2.0"
  val akka = "com.typesafe.akka" %% "akka-actor" % "2.5.2"
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.5.2"
  val scalaReflection = "org.scala-lang" % "scala-reflect" % "2.12.2"
}

lazy val common = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      dep.akka,
      dep.scalaReflection
    )
  )

lazy val server = project
  .settings(
    commonSettings,
    name := "server",
    mainClass in (Compile, run) := Some("server.app.Main"),
    libraryDependencies ++= Seq(
      dep.slick,
      dep.hikaricp,
      dep.h2,
      dep.log4jSlf4jImpl,
      dep.log4jApi,
      dep.log4jCore,
      dep.scalaArm,
      dep.akka,
      dep.akkaSlf4j
    )
  )
  .dependsOn(common)

lazy val client = project
  .settings(
    commonSettings,
    name := "client",
    mainClass in (Compile, run) := Some("client.app.Main"),
    libraryDependencies ++= Seq(
      dep.log4jSlf4jImpl,
      dep.log4jApi,
      dep.log4jCore,
      dep.scalaArm,
      dep.akka,
      dep.akkaSlf4j
    )
  )
  .dependsOn(common)

lazy val root = (project in file("."))
  .aggregate(server, client)
