import sbt._
import sbt.Keys._

object HopBuild extends Build {

  lazy val hop = Project(
    id = "hop",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "hop",
      organization := "net.mcarolan",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.11.5",
      libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"
    )
  )
}
