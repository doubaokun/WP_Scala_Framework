package com.whitepages.framework.logging

import com.persist.JsonOps._
import scala.Some
import LoggingControl._
//import org.joda.time.format.ISODateTimeFormat
//import org.joda.time.DateTime
//import com.whitepages.framework.util.Util._

/**
 * This class provides the methods needed for logging.
 * It is accessed by including one of the traits ClassSupport or ActorSupport.
 */
case class Logger private[framework](private val className: Option[String] = None,
                                     private val actorName: Option[String] = None) {
//                                     private val oldLogger: OldLogger) {


  private def all(level: Int, id: AnyId, msg: => Any, ex: Throwable) {
    val t = System.currentTimeMillis()
    val trace = Thread.currentThread().getStackTrace()(3)
    val line = trace.getLineNumber()
    val file = trace.getFileName
    sendMsg(LogMessage(level, id, t, className, actorName, msg, line, file, ex))
  }


  private def has(id: AnyId, level: Int): Boolean = {
    id match {
      case id1: RequestId =>
        id1.level match {
          case Some(level1) => level <= level1
          case None => false
        }
      case noId => false
    }
  }

  /**
   * Writes a trace level log message.
   * @param id the id of a request or NoId if this message is not associated with a specific request
   * @param msg the Json message to be logged.
   * @param ex  an optional exception to be logged together with its stack trace.
   */
  def trace(id: AnyId, msg: => Json, ex: Throwable = noException) {
    /*
    if (useOld) {
      val msg1 = msg match {
        case s: String => s
        case j: Json => safeCompact(j)
      }
      if (ex == noException) {
        oldLogger.trace(id, msg1)
      } else {
        oldLogger.trace(id, msg1, ex)
      }
    } else {
      if (doTrace || has(id, TRACE)) all(TRACE, id, msg, ex)
    }
    */
    if (doTrace || has(id, TRACE)) all(TRACE, id, msg, ex)
  }


  /**
   * Writes a debug level log message.
   * @param id the id of a request or NoId if this message is not associated with a specific request
   * @param msg the Json message to be logged.
   * @param ex  an optional exception to be logged together with its stack trace.
   */
  def debug(id: AnyId, msg: => Json, ex: Throwable = noException) {
    /*
    if (useOld) {
      val msg1 = msg match {
        case s: String => s
        case j: Json => safeCompact(j)
      }
      if (ex == noException) {
        oldLogger.debug(id, msg1)
      } else {
        oldLogger.debug(id, msg1, ex)
      }
    } else {
      if (doDebug || has(id, DEBUG)) all(DEBUG, id, msg, ex)
    }
    */
    if (doDebug || has(id, DEBUG)) all(DEBUG, id, msg, ex)
  }

  /**
   * Writes an info level log message.
   * @param id the id of a request or NoId if this message is not associated with a specific request
   * @param msg the Json message to be logged.
   * @param ex  an optional exception to be logged together with its stack trace.
   */
  def info(id: AnyId, msg: => Json, ex: Throwable = noException) {
    /*
    if (useOld) {
      val msg1 = msg match {
        case s: String => s
        case j: Json => safeCompact(j)
      }
      if (ex == noException) {
        oldLogger.info(id, msg1)
      } else {
        oldLogger.info(id, msg1, ex)
      }
    } else {
      if (doInfo || has(id, INFO)) all(INFO, id, msg, ex)
    }
    */
    if (doInfo || has(id, INFO)) all(INFO, id, msg, ex)
  }

  /**
   * Writes a warn level log message.
   * @param id the id of a request or NoId if this message is not associated with a specific request
   * @param msg the Json message to be logged.
   * @param ex  an optional exception to be logged together with its stack trace.
   */
  def warn(id: AnyId, msg: => Json, ex: Throwable = noException) {
    /*
    if (useOld) {
      val msg1 = msg match {
        case s: String => s
        case j: Json => safeCompact(j)
      }
      if (ex == noException) {
        oldLogger.warn(id, msg1)
      } else {
        oldLogger.warn(id, msg1, ex)
      }
    } else {
      if (doWarn || has(id, WARN)) all(WARN, id, msg, ex)
    }
    */
    if (doWarn || has(id, WARN)) all(WARN, id, msg, ex)
  }

  /**
   * Writes an error level log message.
   * @param id the id of a request or NoId if this message is not associated with a specific request
   * @param msg the Json message to be logged.
   * @param ex  an optional exception to be logged together with its stack trace.
   */
  def error(id: AnyId, msg: => Json, ex: Throwable = noException) {
    /*
    if (useOld) {
      val msg1 = msg match {
        case s: String => s
        case j: Json => safeCompact(j)
      }
      if (ex == noException) {
        oldLogger.error(id, msg1)
      } else {
        oldLogger.error(id, msg1, ex)
      }
    } else {
      if (doError || has(id, ERROR)) all(ERROR, id, msg, ex)
    }
    */
    if (doError || has(id, ERROR)) all(ERROR, id, msg, ex)
  }

  /**
   * Writes a fatal level log message.
   * @param id the id of a request or NoId if this message is not associated with a specific request
   * @param msg the Json message to be logged.
   * @param ex  an optional exception to be logged together with its stack trace.
   */
  def fatal(id: AnyId, msg: => Json, ex: Throwable = noException) {
    /*
    if (useOld) {
      val msg1 = msg match {
        case s: String => s
        case j: Json => safeCompact(j)
      }
      if (ex == noException) {
        oldLogger.error(id, msg1)
      } else {
        oldLogger.error(id, msg1, ex)
      }
    } else {
      if (doFatal || has(id, FATAL)) all(FATAL, id, msg, ex)
    }
    */
    if (doFatal || has(id, FATAL)) all(FATAL, id, msg, ex)
  }

  /**
   * Write a log message to an alternative log.
   * @param category the category for the message. For log files, this will be part of the file name. The following three
   *                 categories are used by this framework: access, client and time.
   * @param j  Json to be included in the log message.
   * @param time the time to be written in the log. If not specified the default is the time this
   *             method is called.
   */
  def alternative(category: String, j: JsonObject, time: Long = System.currentTimeMillis()) {
    /*
    if (useOld) {
      val t0 = new DateTime(time)
      val logFmt = ISODateTimeFormat.dateTime()
      val t = logFmt.print(t0)
      val all = j ++ JsonObject("T" -> t)
      category match {
        case "access" => accessLog.info(safeCompact(all))
        case "client" => clientLog.info(safeCompact(all))
        case x: Any => error(noId, "Bad alt log name:" + x)
      }
    } else {
      sendMsg(AltMessage(category, time, j ++ JsonObject("@category" -> category)))
    }
    */
    sendMsg(AltMessage(category, time, j ++ JsonObject("@category" -> category)))

  }
}
