/*
package com.whitepages.framework.logging

import org.slf4j.MDC

private[framework] case class OldLogger(logger: org.slf4j.Logger) {
  @inline
  //final def withMdc(logSource: String, logEvent: LogEvent)(logStatement: ⇒ Unit) {
  final def withRequestId(id: AnyId)(logStatement: => Unit) {
    val x = noId
    id match {
      case id: RequestId =>
        MDC.put("trk_id", id.trackingId)
        MDC.put("seq_id", id.spanId)
        try logStatement finally {
          MDC.remove("trk_id")
          MDC.remove("seq_id")
        }
      case x => // noId
        logStatement
    }
  }

  def trace(id: AnyId, msg: => String) {
    withRequestId(id) {
      logger.trace(msg)
    }
  }

  def trace(id: AnyId, msg: => String, t: Throwable) {
    withRequestId(id) {
      logger.trace(msg, t)
    }
  }

  def debug(id: AnyId, msg: => String) {
    withRequestId(id) {
      logger.debug(msg)
    }
  }

  def debug(id: AnyId, msg: => String, t: Throwable) {
    withRequestId(id) {
      logger.debug(msg, t)
    }
  }

  def info(id: AnyId, msg: => String) {
    withRequestId(id) {
      logger.info(msg)
    }
  }

  def info(id: AnyId, msg: => String, t: Throwable) {
    withRequestId(id) {
      logger.info(msg, t)
    }
  }

  def warn(id: AnyId, msg: => String) {
    withRequestId(id) {
      logger.warn(msg)
    }
  }

  def warn(id: AnyId, msg: => String, t: Throwable) {
    withRequestId(id) {
      logger.warn(msg, t)
    }
  }

  def error(id: AnyId, msg: => String) {
    withRequestId(id) {
      logger.error(msg)
    }
  }

  def error(id: AnyId, msg: => String, t: Throwable) {
    withRequestId(id) {
      logger.error(msg, t)
    }
  }
}
*/
