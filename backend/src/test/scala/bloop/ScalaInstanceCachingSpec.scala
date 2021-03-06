package bloop

import utest.{TestSuite, Tests, TestableString, assert}

object ScalaInstanceCachingSpec extends TestSuite {
  val sameVersionPairs = List("2.10.6", "2.10.1", "2.11.11", "2.12.0", "2.12.4")
  val unsharedVersionPairs = List("2.10.6" -> "2.10.4", "2.11.11" -> "2.11.6", "2.12.4" -> "2.12.1")
  val tests = Tests {
    "Scala instances are cached" - {
      "Instances of the same Scala version must share classloaders" - {
        for (version <- sameVersionPairs) {
          val instance1 = ScalaInstance.resolve("org.scala-lang", "scala-compiler", version)
          val instance2 = ScalaInstance.resolve("org.scala-lang", "scala-compiler", version)
          assert(instance1.loader == instance2.loader)
        }

      }

      "Different Scala instances with the same major number do not share the same classloader" - {
        for ((v1, v2) <- unsharedVersionPairs) {
          val instance1 = ScalaInstance.resolve("org.scala-lang", "scala-compiler", v1)
          val instance2 = ScalaInstance.resolve("org.scala-lang", "scala-compiler", v2)
          assert(instance1.loader != instance2.loader)
        }
      }
    }
  }
}
