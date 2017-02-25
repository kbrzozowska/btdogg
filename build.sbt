
resolvers += Resolver.mavenLocal
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
//resolvers += Resolver.jcenterRepo

libraryDependencies ++= {
  val akkaV       = "2.4.17"
  val mldhtV      = "0.0.2-SNAPSHOT"
  val redisScalaV = "1.8.0"
  val scalaTestV  = "3.0.1"
  val scalaCheckV = "1.13.4"
  val scalaMockV  = "3.4.2"
  Seq(
    "com.realizationtime.mldht.core"  %   "libmldht"                    % mldhtV,
    "com.typesafe.akka"               %%  "akka-stream"                 % akkaV,
    "com.github.etaty"                %%  "rediscala"                   % redisScalaV,
    "org.scalatest"                   %%  "scalatest"                   % scalaTestV  % "test",
    "org.scalamock"                   %%  "scalamock-scalatest-support" % scalaMockV  % "test",
    "org.scalacheck"                  %%  "scalacheck"                  % scalaCheckV % "test"
  )
}

lazy val root = (project in file(".")).
  settings(
    organization := "com.realizationtime",
    name := "btdogg",
    version := "0.0.1",
    scalaVersion := "2.12.1"
  )

cancelable in Global := true
fork in run := true
