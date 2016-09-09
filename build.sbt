
resolvers += Resolver.mavenLocal
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
//resolvers += Resolver.jcenterRepo

libraryDependencies ++= {
  val mldhtV      = "0.0.1-SNAPSHOT"
  val akkaV       = "2.4.9"
  val scalaTestV  = "2.2.6"
  val scalaMockV  = "3.2.2"
  val redisScalaV = "1.6.0"
  Seq(
    "com.realizationtime.mldht.core"  %   "libmldht"                    % mldhtV,
    "com.typesafe.akka"               %%  "akka-stream"                 % akkaV,
    "com.github.etaty"                %%  "rediscala"                   % redisScalaV,
    "org.scalatest"                   %%  "scalatest"                   % scalaTestV  % "test",
    "org.scalamock"                   %%  "scalamock-scalatest-support" % scalaMockV  % "test"
  )
}

lazy val root = (project in file(".")).
  settings(
    organization := "com.realizationtime",
    name := "btdogg",
    version := "0.0.1",
    scalaVersion := "2.11.8"
  )

cancelable in Global := true
fork in run := true
