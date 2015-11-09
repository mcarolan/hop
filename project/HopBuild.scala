import sbt._
import sbt.Keys._

object HopBuild extends Build {

  object Versions {
    val http4s = "0.10.1"
    val amqpclient = "3.5.6"
    val scalatest = "2.2.1"
    val argonaut = "6.1"
    val scalaz = "7.1.3"
  }

  lazy val hop = Project(
    id = "hop",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "hop",
      organization := "net.mcarolan",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.11.5",
      libraryDependencies += "org.scalatest" % "scalatest_2.11" % Versions.scalatest % "test",
      libraryDependencies += "com.rabbitmq" % "amqp-client" % Versions.amqpclient,
      libraryDependencies += "org.http4s" %% "http4s-dsl"          % Versions.http4s % "test",
      libraryDependencies += "org.http4s" %% "http4s-blaze-client" % Versions.http4s % "test",
      libraryDependencies += "org.http4s" %% "http4s-argonaut" % Versions.http4s % "test",
      libraryDependencies += "io.argonaut" %% "argonaut" % Versions.argonaut % "test",
      libraryDependencies += "org.scalaz" %% "scalaz-core" % Versions.scalaz,
      libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % Versions.scalaz
    )
  )
}
