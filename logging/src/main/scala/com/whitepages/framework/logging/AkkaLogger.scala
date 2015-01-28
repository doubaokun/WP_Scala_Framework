package com.whitepages.framework.logging

import akka.actor.Actor
import akka.event.Logging._
import LogActorMessages.AkkaMessage
import LoggingLevels._

private[logging] class AkkaLogger extends Actor {

  private def log(level: Int, source: String, clazz: Class[_], msg: Any, cause: Option[Throwable] = None) {
    val time = System.currentTimeMillis()
    val m = AkkaMessage(time, level, source, clazz, msg, cause)
    LoggingState.akkaMsg(m)
  }

  def receive: PartialFunction[Any, Unit] = {
    case InitializeLogger(_) => sender ! LoggerInitialized
    case Error(cause, logSource, logClass, message) =>
      val c = if (cause.toString().contains("NoCause$")) {
        None
      } else {
        Some(cause)
      }
      log(ERROR, logSource, logClass, message, c)
    case Warning(logSource, logClass, message) => log(WARN, logSource, logClass, message)
    case Info(logSource, logClass, message) => log(INFO, logSource, logClass, message)
    case Debug(logSource, logClass, message) => log(DEBUG, logSource, logClass, message)
  }
}
