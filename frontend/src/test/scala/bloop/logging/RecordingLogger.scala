package bloop.logging

import scala.collection.mutable.Buffer

class RecordingLogger extends AbstractLogger {
  private val messages: Buffer[(String, String)] = Buffer.empty
  def clear(): Unit = messages.clear()
  def getMessages(): List[(String, String)] = messages.toList

  override val name: String = "RecordingLogger"
  override val ansiCodesSupported: Boolean = true

  override def verbose[T](op: => T): T = op
  override def debug(msg: String): Unit = messages += (("debug", msg))
  override def info(msg: String): Unit = messages += (("info", msg))
  override def error(msg: String): Unit = messages += (("error", msg))
  override def warn(msg: String): Unit = messages += (("warn", msg))
  private def trace(msg: String): Unit = messages += (("trace", msg))
  override def trace(ex: Throwable): Unit = {
    ex.getStackTrace.foreach(ste => trace(ste.toString))
    Option(ex.getCause).foreach { cause =>
      trace("Caused by:")
      trace(cause)
    }
  }
}
