
resolvers += Resolver.mavenLocal
resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
//resolvers += Resolver.jcenterRepo

val rootDependencies = {
  val akkaV = "2.5.4"
  val mldhtV = "0.0.2-SNAPSHOT"
  val reactiveMongoV = "0.12.5"
  val elastic4sV = "5.4.3"
  val jacksonJsr310 = "2.8.9"
  val redisScalaV = "1.8.0"
  val scalaTestV = "3.0.1"
  val scalaCheckV = "1.13.4"
  val mockitoV = "2.8.9"
  //  val scalaMockV     = "3.5.0"
  val logbackV = "1.2.3"
  val scalaLoggingV = "3.5.0"
  Seq(
    "com.realizationtime.mldht.core" % "libmldht" % mldhtV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "org.reactivemongo" %% "reactivemongo" % reactiveMongoV,
    "org.reactivemongo" %% "reactivemongo-akkastream" % reactiveMongoV,
    "com.sksamuel.elastic4s" %% "elastic4s-tcp" % elastic4sV excludeAll(
      ExclusionRule(organization = "io.netty", name = "netty-buffer"),
      ExclusionRule(organization = "io.netty", name = "netty-transport"),
      ExclusionRule(organization = "io.netty", name = "netty-codec"),
      ExclusionRule(organization = "io.netty", name = "netty-codec-http"),
      ExclusionRule(organization = "io.netty", name = "netty-handler"),
      ExclusionRule(organization = "io.netty", name = "netty-common")
    ),
    "com.sksamuel.elastic4s" %% "elastic4s-streams" % elastic4sV,
    "com.sksamuel.elastic4s" %% "elastic4s-jackson" % elastic4sV,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonJsr310,
    "com.github.etaty" %% "rediscala" % redisScalaV,
    "org.scalatest" %% "scalatest" % scalaTestV % "test",
    "org.mockito" % "mockito-core" % mockitoV % "test",
    //    "org.scalamock"                   %%  "scalamock-scalatest-support" % scalaMockV  % "test",
    "org.scalacheck" %% "scalacheck" % scalaCheckV % "test",
    "ch.qos.logback" % "logback-classic" % logbackV,
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
  )
}

val frontendDependencies = {
  val ficusV      = "1.4.1"
  val akkaHttpV   = "10.0.9"
  Seq(
    "com.iheart"        %% "ficus"                % ficusV,
    "com.typesafe.akka" %% "akka-http"            % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
  )
}

assemblyMergeStrategy in assembly := {
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

lazy val commonSettings = Seq(
  organization := "com.realizationtime",
  version := "0.0.1",
  scalaVersion := "2.12.3"
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "btdogg",
    libraryDependencies ++= rootDependencies
  )

lazy val frontendEmber = (project in file("frontend/ember"))
  .settings(
    commonSettings
  )

lazy val frontend = (project in file("frontend/server"))
  .aggregate(frontendEmber)
  .settings(
    commonSettings,
    libraryDependencies ++= frontendDependencies
  )

mainClass in Compile := Some("com.realizationtime.btdogg.BtDoggMain")

cancelable in Global := true
fork in run := true
javaOptions += "-Xmx2500M"
