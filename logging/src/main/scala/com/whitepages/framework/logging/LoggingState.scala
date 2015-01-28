package com.whitepages.framework.logging

import akka.actor._
import com.whitepages.framework.logging.LogActorMessages._
import scala.language.existentials
import scala.concurrent.Promise
import scala.collection.mutable
import TimeActorMessages._

/**
 * Global state info for logging. Use with care!
 */
private[whitepages] object LoggingState extends ClassLogging {

  // Queue of messages sent before logger is started
  private[logging] val msgs = new mutable.Queue[LogActorMessage]()

  var doTrace: Boolean = false
  var doDebug: Boolean = false
  var doInfo: Boolean = true
  var doWarn: Boolean = true
  var doError: Boolean = true
  val doFatal: Boolean = true
  private[logging] var slf4jLogLevel = LoggingLevels.INFO

  private[logging] var loggerStopping = false
  private[logging] var logger: Option[ActorRef] = None
  private[logging] var isDev = false
  private[logging] val noException = new Exception("No Exception")
  private[logging] var doTime: Boolean = false
  private[logging] var timeActorOption: Option[ActorRef] = None

  // Use to sync akka logging actor shutdown
  private[logging] val stopPromise = Promise[Unit]

  private[logging] def sendMsg(msg: LogActorMessage) {
    if (loggerStopping) {
      if (isDev) println(s"*** Log message received after logger shutdown: $msg")
    } else {
      logger match {
        case Some(a) => a ! msg
        case None =>
          msgs.synchronized {
            msgs.enqueue(msg)
          }
      }
    }
  }

  private[logging] def sendSlf4jMsg(msg: LogMessage) {
    if (msg.level >= slf4jLogLevel) {
      sendMsg(msg)
    }
  }

  private[logging] def akkaMsg(m: AkkaMessage) {
    if (m.msg == "DIE") {
      stopPromise.trySuccess(())
    } else {
      // if already completed
      // TODO manage akka logging levels???
      if (m.level > LoggingLevels.INFO) {
        sendMsg(m)
      }
    }
  }

  private[logging] def timeStart(id: RequestId, name: String, uid: String) {
    timeActorOption foreach {
      case timeActor =>
        val time = System.nanoTime() / 1000
        timeActor ! TimeStart(id, name, uid, time)
    }
  }

  private[logging] def timeEnd(id: RequestId, name: String, uid: String) {
    timeActorOption foreach {
      case timeActor =>
      val time = System.nanoTime() / 1000
      timeActor ! TimeEnd(id, name, uid, time)
    }
  }

}
