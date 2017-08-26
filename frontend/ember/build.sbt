lazy val compileEmber = taskKey[Unit]("compiles ember project and copies it to frontend/server/src/main/resources/ember_static/")

compileEmber := {
  import scala.sys.process._
  (Process(Seq("ember", "build", "-prod"), new File("frontend/ember")) !) match {
    case 0 =>
    case _ =>
      sys.error("Ember compilation failed")
  }
}

(compile in Compile) := {
  compileEmber.value
  (compile in Compile).value
}
