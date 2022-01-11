ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

lazy val root = (project in file("."))
  .settings(
    name := "staruml_dbutil",
    // idePackagePrefix := Some("wangzx.startuml_dbutil"),
    libraryDependencies := Seq(
      "com.lihaoyi" %% "mainargs"  % "0.2.2",
      "com.lihaoyi" % "ujson_2.13" % "1.4.4",
      "com.lihaoyi" %% "fansi" % "0.3.0",
      "com.lihaoyi" %% "os-lib" % "0.8.0",

      "com.h2database" % "h2" % "2.0.206",
      "mysql" % "mysql-connector-java" % "8.0.27",
      "com.github.wangzaixiang" %% "scala-sql" % "2.0.7",
      "org.ow2.asm" % "asm" % "9.2",
    )
  )
