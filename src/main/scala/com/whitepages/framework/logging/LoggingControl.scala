package com.whitepages.framework.logging

import akka.actor._
import org.slf4j.LoggerFactory
import com.persist.JsonOps._
import scala.language.existentials
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.service.BaseService

// Goals
// * 1. All via single Scala Actor
// * 2. support Json and string logging
// * 3. Support exceptions and stack trace
// * 4. All logs via a single stream on prod
// * 5. Logs to local files and stdout on dev
// * 6. Timestamp at log.xxx call (include tz)
// * 7. Set log level dynamically
// * 8. Set log level per request
// * 9. log calls can include id
// * 10. Capture actor name if in actor
// * 11. Capture class name and line number if in class or object
// * 12. In WS support both new and old form API (for a while)
// * 13. Feature switch to send new api to old logback handlers.
// 14. Use UDP appender from Jack/Jonathan.
// * 15. Support trace and fatal level logging
// * 16. Pretty (remove std keys) on dev stdout
// * 17. Easier to customize and debug (all scala)
// * 18. Support new WP std json log format


// TODO should be private[framework] after client move
private[whitepages] object LoggingControl extends ClassSupport {

  // Messages for Log Actor

  private[logging] trait LogActorMessage

  private[logging] case class LogMessage(level: Int, id: AnyId,
                                         time: Long, className: Option[String], actorName: Option[String], msg: Json,
                                         line: Int, file: String, ex: Throwable, kind: String = "") extends LogActorMessage

  private[logging] case class AltMessage(category: String, time: Long, j: JsonObject) extends LogActorMessage

  private[logging] case class AkkaMessage(time: Long, level: Int, source: String, clazz: Class[_], msg: Any, cause: Option[Throwable])
    extends LogActorMessage

  private[logging] case object LastAkkaMessage extends LogActorMessage

  private[logging] case object StopLogging extends LogActorMessage

  private[logging] trait TimeActorMessage

  private[logging] case class TimeStart(id: RequestId, name: String, uid: String, time: Long) extends TimeActorMessage

  private[logging] case class TimeEnd(id: RequestId, name: String, uid: String, time: Long) extends TimeActorMessage

  val TRACE = 0
  val DEBUG = 1
  val INFO = 2
  val WARN = 3
  val ERROR = 4
  val FATAL = 5

  private[framework] var monitor0: ActorRef = null

  private[framework] var serviceName0: String = ""

  private[framework] var hostName0: String = ""

  private[framework] var hostIp0: String = ""

  private[framework] var service: BaseService = null

  private[framework] var system0: ActorSystem = null

  private[this] val levels = Array("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")

  private[this] var isStarted0 = false

  val noException = new Exception("No Exception")

  def isStarted = isStarted0

  private[this] var isDev0 = false

  def isDev = isDev0

  private[this] var useOld0 = false

  def useOld = useOld0

  private[this] var stopping0 = false

  private[framework] def stopping = stopping0

  private var logActor: ActorRef = null
  private var timeActor: ActorRef = null
  //private[logging] var accessLog: org.slf4j.Logger = null
  //private[logging] var clientLog: org.slf4j.Logger = null

  private[this] val stopPromise = Promise[Unit]

  // Queue of messages sent before logger is started
  private[this] val msgs = new mutable.Queue[LogActorMessage]()

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

  private[this] var globalPercent: Int = 100
  private[this] var warmupPercent = 100

  def start(version: String, host: String, ip: String,
            isDev: Boolean, useOld: Boolean, useUDP: Boolean, forceLocal: Boolean, logStdout: Boolean,
            logPath: String) {
    LoggerFactory.getLogger("ROOT")
    logActor = system.actorOf(Props(classOf[LogActor], version, host, isDev, useUDP, forceLocal, logStdout, logPath), name = "SvcLogActor")
    timeActor = system.actorOf(Props(classOf[TimeActor]), name = "SvcTimeActor")
    useOld0 = useOld
    isDev0 = isDev
    hostName0 = host
    hostIp0 = ip
    isStarted0 = true
    stopping0 = false
    //accessLog = LoggerFactory.getLogger("com.whitepages.logging.access_log")
    //clientLog = LoggerFactory.getLogger("com.whitepages.logging.client_log")
    globalPercent = if (isDev) config.getInt("wp.client.devPercent") else 100
    warmupPercent = (config.getInt("wp.client.warmupPercent")) * globalPercent / 100
    doTime = (config.getBoolean("wp.service.time"))
    levelStringToInt(config.getString("wp.logging.slf4jLogLevel")) foreach {
      case level => slf4jLogLevel = level
    }

    msgs.synchronized {
      if (msgs.size > 0) {
        log.info(noId, s"Saw ${msgs.size} messages before logger start")
        for (msg <- msgs) {
          sendMsg(msg)
        }
        msgs.clear()
      }
    }
  }

  private[this] var warmup: Boolean = false

  private[framework] def setWarmup(v: Boolean) {
    warmup = v
  }

  private[framework] def getPercent = {
    if (warmup) warmupPercent else globalPercent
  }

  private[logging] def sendMsg(msg: LogActorMessage) {
    if (stopping0) {
      if (isDev) println(s"*** Log message received after logger shutdown: $msg")
    } else if (isStarted) {
      logActor ! msg
    } else {
      msgs.synchronized {
        msgs.enqueue(msg)
      }
    }
  }

  private[logging] def sendSlf4jMsg(msg: LogMessage) {
    if (msg.level >= slf4jLogLevel) {
      sendMsg(msg)
    }
  }

  def stop(): Future[Unit] = {
    timeActor ! PoisonPill
    // Wait for all akka messages to be processed
    if (!useOld) {
      sendMsg(LastAkkaMessage)
      stopPromise.future.map {
        case x =>
          sendMsg(StopLogging)
          stopping0 = true
      }
    } else {
      // TODO stop appenders!
      Future {}
    }
  }

  var doTrace: Boolean = true
  var doDebug: Boolean = true
  var doInfo: Boolean = true
  var doWarn: Boolean = true
  var doError: Boolean = true
  val doFatal: Boolean = true

  var doTime: Boolean = true

  var slf4jLogLevel: Int = levelStringToInt("info").get

  def setLevel(level: Int) {
    val doTrace1 = level <= TRACE
    if (doTrace != doTrace1) doTrace = doTrace1
    val doDebug1 = level <= DEBUG
    if (doDebug != doDebug1) doDebug = doDebug1
    val doInfo1 = level <= INFO
    if (doInfo != doInfo1) doInfo = doInfo1
    val doWarn1 = level <= WARN
    if (doWarn != doWarn1) doWarn = doWarn1
    val doError1 = level <= ERROR
    if (doError != doError1) doError = doError1
  }

  private[logging] def akkaMsg(m: AkkaMessage) {
    if (m.msg == "DIE") {
      stopPromise.trySuccess(())
    } else {
      // if already completed
      // TODO manage akka logging levels???
      if (m.level > INFO) {
        sendMsg(m)
      }
    }
  }

  private[framework] def timeStart(id: RequestId, name: String, uid: String) {
    val time = System.nanoTime() / 1000
    timeActor ! TimeStart(id, name, uid, time)
  }

  private[framework] def timeEnd(id: RequestId, name: String, uid: String) {
    val time = System.nanoTime() / 1000
    timeActor ! TimeEnd(id, name, uid, time)
  }

}
