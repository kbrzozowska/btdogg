
resolvers in ThisBuild += Resolver.mavenLocal
resolvers in ThisBuild += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
//resolvers += Resolver.jcenterRepo

val commonsDependencies = {
  import LibVersions._
  Seq(
    "com.realizationtime.mldht.core" % "libmldht" % mldhtV,
    "org.reactivemongo"          %% "reactivemongo" % reactiveMongoV,
    "org.reactivemongo"          %% "reactivemongo-akkastream" % reactiveMongoV,
  )
}

val rootDependencies = {
  import LibVersions._
  Seq(
    "com.realizationtime.mldht.core" % "libmldht" % mldhtV,
    "com.typesafe.akka"          %% "akka-stream" % akkaV,
    "com.sksamuel.elastic4s"     %% "elastic4s-tcp" % elastic4sV excludeAll(
      ExclusionRule(organization = "io.netty", name = "netty-buffer"),
      ExclusionRule(organization = "io.netty", name = "netty-transport"),
      ExclusionRule(organization = "io.netty", name = "netty-codec"),
      ExclusionRule(organization = "io.netty", name = "netty-codec-http"),
      ExclusionRule(organization = "io.netty", name = "netty-handler"),
      ExclusionRule(organization = "io.netty", name = "netty-common")
    ),
    "com.sksamuel.elastic4s"     %% "elastic4s-streams" % elastic4sV,
    "com.sksamuel.elastic4s"     %% "elastic4s-jackson" % elastic4sV,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonJsr310,
    "com.github.etaty"           %% "rediscala" % redisScalaV,
    "org.scalatest"              %% "scalatest" % scalaTestV % "test",
    "org.mockito"                 % "mockito-core" % mockitoV % "test",
    //    "org.scalamock"                   %%  "scalamock-scalatest-support" % scalaMockV  % "test",
    "org.scalacheck"             %% "scalacheck" % scalaCheckV % "test",
    "ch.qos.logback"              %  "logback-classic" % logbackV,
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
  )
}

val frontendDependencies = {
  import LibVersions._
  Seq(
    "com.iheart"        %% "ficus" % ficusV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
  )
}

assemblyMergeStrategy in assembly := {
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

lazy val sharedSettings = Seq(
  organization := "com.realizationtime",
  version := "0.0.1",
  scalaVersion := "2.12.3"
)

lazy val commons = project
  .settings(
    sharedSettings,
    libraryDependencies ++= commonsDependencies
  )

lazy val root = (project in file("."))
  .dependsOn(commons)
  .settings(
    sharedSettings,
    name := "btdogg",
    libraryDependencies ++= rootDependencies
  )

lazy val frontendEmber = (project in file("frontend/ember"))
  .settings(
    sharedSettings
  )

lazy val frontend = (project in file("frontend/server"))
  .aggregate(frontendEmber)
  .dependsOn(commons)
  .settings(
    sharedSettings,
    libraryDependencies ++= frontendDependencies
  )

mainClass in Compile := Some("com.realizationtime.btdogg.BtDoggMain")

cancelable in Global := true
fork in run := true
javaOptions += "-Xmx2500M"
