package com.whitepages.framework.logging

import akka.actor.{Props, ActorSystem}
import com.typesafe.config.Config
import com.whitepages.framework.logging.LogActorMessages.{StopLogging, LastAkkaMessage}
import com.whitepages.framework.logging.TimeActorMessages.TimeDone
import scala.concurrent.{Await, Promise, ExecutionContext, Future}
import scala.language.postfixOps

/**
 *
 * @param system the actor system.
 * @param loggingConfig normally the config at wp.logging.
 * @param serviceName name of the service (to log).
 * @param serviceVersion version of the service (to log).
 * @param host host name (to log).
 * @param isDev developer mode.
 * @param gcLogAct an optional way to intercept gc logging messages.
 */
case class LoggingSystem(private val system: ActorSystem,
                         private val loggingConfig: Config,
                         private val serviceName: String,
                         private val serviceVersion: String,
                         private val host: String,
                         private val isDev: Boolean,
                         private val gcLogAct: GcLogAct = DefaultGcLogAct) extends ClassLogging {

  import LoggingLevels._

  private[this] implicit val ec: ExecutionContext = system.dispatcher
  private[this] val done = Promise[Unit]
  private[this] val tdone = Promise[Unit]

  private[this] val slf4jLogLevel: Int = levelStringToInt(loggingConfig.getString("slf4jLogLevel")) match {
    case Some(level) => level
    case None => INFO
  }
  private[this] val level = loggingConfig.getString("logLevel")

  // Make sure all top level LoggingState vars are reset!
  // Otherwise multiple tests may interfer
  setLevel(level)
  LoggingState.isDev = isDev
  LoggingState.slf4jLogLevel = slf4jLogLevel
  LoggingState.loggerStopping = false
  LoggingState.doTime = false
  LoggingState.timeActorOption = None

  private[this] val logActor = system.actorOf(Props(classOf[LogActor], done, loggingConfig, serviceName, serviceVersion, host, isDev), name = "SvcLogActor")
  LoggingState.logger.synchronized {
    LoggingState.logger = Some(logActor)
  }

  val gc = loggingConfig.getBoolean("gc")
  val gcLogger: Option[GcLogger] = if (gc) {
    Some(GcLogger(gcLogAct))
  } else {
    None
  }

  val time = loggingConfig.getBoolean("time")
  if (time) {
    // Start timing actor
    LoggingState.doTime = true
    val timeActor = system.actorOf(Props(classOf[TimeActor], tdone), name = "TimeActor")
    LoggingState.timeActorOption = Some(timeActor)
  } else {
    tdone.success(())
  }

  // Deal with messages send before logger was ready
  LoggingState.msgs.synchronized {
    if (LoggingState.msgs.size > 0) {
      log.info(noId, s"Saw ${LoggingState.msgs.size} messages before logger start")
      for (msg <- LoggingState.msgs) {
        LoggingState.sendMsg(msg)
      }
    }
    LoggingState.msgs.clear()
  }

  /**
   *
   * @param level the new logging level.
   */
  def setLevel(level: String): Unit = {

    import LoggingState._

    val ilevel = levelStringToInt(level).get
    val doTrace1 = ilevel <= TRACE
    if (doTrace != doTrace1) doTrace = doTrace1
    val doDebug1 = ilevel <= DEBUG
    if (doDebug != doDebug1) doDebug = doDebug1
    val doInfo1 = ilevel <= INFO
    if (doInfo != doInfo1) doInfo = doInfo1
    val doWarn1 = ilevel <= WARN
    if (doWarn != doWarn1) doWarn = doWarn1
    val doError1 = ilevel <= ERROR
    if (doError != doError1) doError = doError1
  }

  /**
   *
   * @return  future completes when logger is shut down.
   */
  def stop: Future[Unit] = {
    gcLogger foreach (_.stop)
    LoggingState.sendMsg(LastAkkaMessage)
    LoggingState.timeActorOption foreach {
      case timeActor => timeActor ! TimeDone
    }
    (LoggingState.stopPromise.future zip tdone.future).flatMap {
      case x =>
        LoggingState.sendMsg(StopLogging)
        done.future map {
          case x =>
            LoggingState.loggerStopping = true
        }
    }
  }

}
