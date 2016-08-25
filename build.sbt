
resolvers += Resolver.mavenLocal
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

//resolvers += Resolver.jcenterRepo

libraryDependencies ++= {
  val mldhtV      = "0.0.1-SNAPSHOT"
  val scalaTestV  = "2.2.6"
  val scalaMockV  = "3.2.2"
  Seq(
    "com.realizationtime.mldht.core"  %   "libmldht"                    % mldhtV,
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
