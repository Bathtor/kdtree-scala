import SonatypeKeys._
import sbtrelease._
import ReleaseStateTransformations._

sonatypeSettings

organization := "com.thesamet"

name := "kdtree"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

scalacOptions ++= Seq("-unchecked", "-deprecation")

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.11.5", "2.10.4")

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

releaseSettings

pomExtra := (
  <url>https://github.com/thesamet/kdtree-scala</url>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/thesamet/kdtree-scala</url>
    <connection>scm:git:git@github.com:thesamet/kdtree-scala.git</connection>
  </scm>
  <developers>
    <developer>
      <id>thesamet</id>
      <name>Nadav Samet</name>
      <url>http://www.thesamet.com</url>
    </developer>
  </developers>
)

