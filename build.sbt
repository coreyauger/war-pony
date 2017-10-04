import SonatypeKeys._

import sbt.Keys._

sonatypeSettings

name := "war-pony"

version := "0.0.1-SNAPSHOT"

organization := "io.surfkit"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= deps

scalaVersion := "2.11.11"

fork := true

lazy val deps = {
  val akkaV = "2.5.3"
  Seq(
    "com.typesafe.akka"       %% "akka-actor"                 % akkaV,
    "com.typesafe.akka"       %% "akka-stream"                % akkaV,
    "com.typesafe.akka"       %% "akka-http"                  % "10.0.9",
    "com.typesafe.play"       %% "play-json"                  % akkaV,
    "com.typesafe.play"       %% "play-json-joda"             % "2.6.0",
    "de.heikoseeberger"       %% "akka-http-play-json"        % "1.17.0"
  )
}

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
)

homepage := Some(url("http://www.surfkit.io/"))

licenses += ("MIT License", url("http://www.opensource.org/licenses/mit-license.php"))

