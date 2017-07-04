package simple

object Main {
  def main(args: Array[String]): Unit = {
  }

  def unsafe(): Unit = {
    println(s"Current time is ${java.time.OffsetDateTime.now()}")
  }

  def deprecated(): Unit = {
    new java.io.File(".").toURL()
  }

  def nonPortable(): Unit = {
    sun.misc.Unsafe.getUnsafe()
  }
}
