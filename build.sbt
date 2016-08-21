
resolvers += Resolver.mavenLocal

libraryDependencies ++= {
  val mldhtV = "0.0.1-SNAPSHOT"
  Seq(
    "com.realizationtime.mldht.core"  % "libmldht"  % mldhtV
  )
}

lazy val root = (project in file(".")).
  settings(
    organization := "com.realizationtime",
    name := "btdogg",
    version := "0.0.1",
    scalaVersion := "2.11.8"
  )
