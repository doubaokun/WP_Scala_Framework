package com.whitepages.framework.logging

private[framework] object LoggingLevels {

  val TRACE = 0
  val DEBUG = 1
  val INFO = 2
  val WARN = 3
  val ERROR = 4
  val FATAL = 5

  private[this] val levels = Array("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")

  def levelStringToInt(s: String): Option[Int] = {
    val i = levels.indexOf(s.toUpperCase())
    if (i >= 0) {
      Some(i)
    } else {
      None
    }
  }

  def levelIntToString(i: Int): Option[String] = {
    if (0 <= i && i < levels.size) {
      Some(levels(i))
    } else {
      None
    }
  }
}
