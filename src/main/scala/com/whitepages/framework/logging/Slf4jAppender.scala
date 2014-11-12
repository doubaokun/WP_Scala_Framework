package com.whitepages.framework.logging

import ch.qos.logback.core.{Appender, UnsynchronizedAppenderBase}
import ch.qos.logback.core.spi.AppenderAttachable
import LoggingControl._
import com.whitepages.framework.util.ClassSupport

private[logging] class Slf4jAppender[E]() extends UnsynchronizedAppenderBase[E] with AppenderAttachable[E] with ClassSupport {

  val appenders = scala.collection.mutable.HashSet[Appender[E]]()

  def detachAndStopAllAppenders(): Unit = {}

  def detachAppender(x$1: String): Boolean = true

  def detachAppender(x$1: ch.qos.logback.core.Appender[E]): Boolean = true

  def getAppender(x$1: String): ch.qos.logback.core.Appender[E] = null

  def isAttached(x$1: ch.qos.logback.core.Appender[E]): Boolean = true

  def iteratorForAppenders(): java.util.Iterator[ch.qos.logback.core.Appender[E]] = null

  def addAppender(a: Appender[E]) {
    appenders.add(a)
  }

  def append(event: E) {
    if (LoggingControl.useOld) {
      for (appender <- appenders) {
        appender.doAppend(event)
      }
    } else {
      event match {
        case e: ch.qos.logback.classic.spi.LoggingEvent =>
          val frame = e.getCallerData()(0)
          val level = LoggingControl.levelStringToInt(e.getLevel.toString) match {
            case Some(i) => i
            case None => LoggingControl.INFO
          }
          val ex = try {
            val x = e.getThrowableProxy.asInstanceOf[ch.qos.logback.classic.spi.ThrowableProxy]
            x.getThrowable
          } catch {
            case ex: Any => LoggingControl.noException
          }
          val msg = LogMessage(level, noId, e.getTimeStamp, Some(frame.getClassName), None,
            e.getFormattedMessage, frame.getLineNumber, frame.getFileName, ex, "slf4j")
          LoggingControl.sendSlf4jMsg(msg)
        case x: Any =>
          log.warn(noId, "UNEXPECTED EVENT:" + event.getClass + ":" + event)
      }
    }
  }
}
