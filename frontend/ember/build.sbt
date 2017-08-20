lazy val compileEmber = taskKey[Unit]("compiles ember project and copies it to frontend/server/src/main/resources/ember_static/")

compileEmber := {
  import scala.sys.process._
  val res = "ember dsakjiodsjidjasisdjsaiji" !
//  if (res != 0)
//    throw new IllegalStateException("Ember compilation failed")
}

(compile in Compile) := {
  compileEmber.value
  (compile in Compile).value
}

//lazy val helloWorld = taskKey[Unit]("prints 'hello world'")
//
//helloWorld := {
//  println("hello world")
//}
