package com.whitepages.framework.logging

import akka.actor.{Props, ActorRef, PoisonPill}
import com.typesafe.config.Config
import com.whitepages.framework.exceptions.FrameworkException
import org.joda.time.format.ISODateTimeFormat
import com.persist.JsonOps._
import LoggingState._
import scala.concurrent.Promise
import scala.language.postfixOps
import LogActorMessages._
import LoggingLevels._
import com.whitepages.framework.util.JsonUtil._

private[logging] class LogActor(done: Promise[Unit], loggingConfig: Config, serviceName: String, serviceVersion: String, host: String, isDev: Boolean) extends ActorLogging {

  private[this] val logFmt = ISODateTimeFormat.dateTime()
  private[this] val system = context.system
  private[this] val useUDP = loggingConfig.getBoolean("useUdp")
  private[this] val forceLocal = loggingConfig.getBoolean("forceLocal")
  private[this] val forceUdp = loggingConfig.getBoolean("forceUdp")
  private[this] val logStdout = loggingConfig.getBoolean("logStdout")
  private[this] val logPath = loggingConfig.getString("logPath")

  val udpAppender: ActorRef = if (useUDP) {
    val host = loggingConfig.getString("udpHost")
    val port = loggingConfig.getInt("udpPort")

    //for (n<-java.net.NetworkInterface.getNetworkInterfaces) {
    //  log.info(noId,n.getName)
    //}
    val mtuSize = try {
      java.net.NetworkInterface.getByName("lo").getMTU
    } catch {
      case ex: Throwable =>
        try {
          // OSX
          //log.warn(noId, "networkinteface lo failed", ex)
          java.net.NetworkInterface.getByName("lo0").getMTU
        } catch {
          case ex: Throwable =>
            //log.warn(noId, "networkinteface lo0 failed", ex)
            log.warn(noId, "can't get mtuSize1")
            system.settings.config.getLong("wp.logging.udpMTU")
        }

    }

    log.info(noId, JsonObject("mtuSize" -> mtuSize))
    if (mtuSize < 64000) log.warn(noId, JsonObject("msg" -> "UDP  buffer too small", "mtuSize" -> mtuSize))
    context.actorOf(Props(classOf[UdpAppender], host, port, mtuSize), name = "UDPLogger")
  } else {
    null
  }

  private[this] val headers = JsonObject("@version" -> 1, "@host" -> host,
    "@service" -> JsonObject("name" -> serviceName, "version" -> serviceVersion))

  private[this] val commonHeaders = headers ++ JsonObject("@category" -> "common")

  private def exToJson(ex: Throwable): Json = {
    val name = ex.getClass.toString()
    ex match {
      case ex: FrameworkException =>
        JsonObject("ex" -> name, "msg" -> ex.msg)
      case ex: Throwable =>
        JsonObject("ex" -> name, "msg" -> ex.getMessage)
    }
  }

  private def exceptionJson(ex: Throwable): JsonObject = {
    val stack = ex.getStackTrace map {
      case trace =>
        val j0 = if (trace.getLineNumber > 0) {
          JsonObject("line" -> trace.getLineNumber)
        } else {
          emptyJsonObject
        }
        val j1 = JsonObject(
          "class" -> trace.getClassName,
          "file" -> trace.getFileName,
          "method" -> trace.getMethodName
        )
        j0 ++ j1
    }
    val cause = ex.getCause
    val j1 = if (cause == null) {
      emptyJsonObject
    } else {
      JsonObject("cause" -> exceptionJson(cause))
    }
    JsonObject("exception" -> JsonObject("msg" -> exToJson(ex), "stack" -> stack.toSeq)) ++ j1

  }

  private[this] val fileAppenders = scala.collection.mutable.HashMap[String, FileAppender]()

  private def append(msg: Json, category: String) {
    if (useUDP) {
      udpAppender ! safeCompact(msg)
    }
    if (!useUDP || forceLocal || isDev) {
      val fa = fileAppenders.get(category) match {
        case Some(a) => a
        case None =>
          val a = FileAppender(context, serviceName, logPath, category)
          fileAppenders += (category -> a)
          a
      }
      val date = jgetString(msg, "@timestamp").substring(0, 10)
      fa.add(date, safeCompact(msg))
    }
  }

  def receive = {
    case LogMessage(level: Int, id: AnyId, time: Long, className: Option[String], actorName: Option[String], msg: Json,
    line: Int, file: String, ex: Throwable, kind: String) =>
      val t = logFmt.print(time)
      val levels = levelIntToString(level) match {
        case Some(s) => s
        case None => "UNKNOWN"
      }
      val j = JsonObject("@timestamp" -> t, "msg" -> msg, "file" -> file, "@severity" -> levels)
      val j0 = if (line > 0) {
        JsonObject("line" -> line)
      } else {
        emptyJsonObject
      }
      val j1 = className match {
        case Some(className) => JsonObject("class" -> className)
        case None => emptyJsonObject
      }
      val j2 = actorName match {
        case Some(actorName) => JsonObject("actor" -> actorName)
        case None => emptyJsonObject
      }
      val j3 = if (ex == noException) {
        emptyJsonObject
      } else {
        exceptionJson(ex)
      }
      val j4 = id match {
        case RequestId(trackingId, spanId, level) =>
          JsonObject("@traceId" -> JsonArray(trackingId, spanId))
        case noId => emptyJsonObject
      }
      val j5 = if (kind == "") {
        emptyJsonObject
      } else {
        JsonObject("kind" -> kind)
      }
      // TODO prod to udp appender
      val shortMsg = j ++ j0 ++ j1 ++ j2 ++ j3 ++ j4 ++ j5
      if (isDev && logStdout) {
        println(s"${
          safePretty(shortMsg)
        }")
      }
      append(commonHeaders ++ shortMsg, "common")

    case AltMessage(category, time, j) =>
      val t = logFmt.print(time)
      append(headers ++ j ++ JsonObject("@timestamp" -> t), category)

    case m@AkkaMessage(time, level, source, clazz, msg, cause) =>
      val msg1 = if (msg == null) "UNKNOWN" else msg
      val t = logFmt.print(time)
      val eee = cause match {
        case Some(ex) => exceptionJson(ex)
        case None => emptyJsonObject
      }
      val levels = levelIntToString(level) match {
        case Some(s) => s
        case None => "UNKNOWN"
      }
      val shortMsg = JsonObject("@timestamp" -> t, "kind" -> "akka", "msg" -> msg1.toString(), "source" -> source,
        "@severity" -> levels, "class" -> clazz.getName()) ++ eee
      append(commonHeaders ++ shortMsg, "common")
      if (isDev && logStdout) {
        println(s"${
          safePretty(shortMsg)
        }")
      }

    case LastAkkaMessage =>
      val akkaLog = akka.event.Logging(context.system, this)
      akkaLog.error("DIE")

    case StopLogging =>
      for ((category, appender) <- fileAppenders) {
        appender.close()
      }
      if (udpAppender != null) context.stop(udpAppender)
      // TODO stop or post stop
      self ! PoisonPill
      done.success(())

    case msg: Any =>
      log.warn(noId, "Unrecognized LogActor message:" + msg)
  }
}
