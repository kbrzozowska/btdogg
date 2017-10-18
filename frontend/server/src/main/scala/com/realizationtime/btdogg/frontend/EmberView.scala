package com.realizationtime.btdogg.frontend

class EmberView[T](val content: Seq[T], pagesCount: Long, itemsCount: Long) {

  val meta = Map("pagesCount" -> pagesCount, "itemsCount" -> itemsCount)

}

object EmberView {
  def apply[T](content: Seq[T]): EmberView[T] = new EmberView(content, 1L, content.size)
  def apply[T](t: T): EmberView[T] = EmberView(List(t))
  def apply[T](tOpt: Option[T]): EmberView[T] = tOpt match {
    case Some(t) => EmberView(t)
    case None => EmberView(List())
  }
}
