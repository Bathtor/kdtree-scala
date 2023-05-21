organization := "com.thesamet"

name := "kdtree"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % "test"

scalacOptions ++= Seq("-unchecked", "-deprecation")

scalaVersion := "3.2.2"

crossScalaVersions := Seq("3.2.2", "2.13.10")

version := "1.0.6-SNAPSHOT"
