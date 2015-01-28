package com.whitepages.framework.server

import com.persist.JsonOps._
import scala.concurrent.{ExecutionContext, Future}
import spray.can.server.Stats
import spray.can.Http
import akka.actor.{ActorRef, ActorContext}
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.collection.JavaConversions._
import com.whitepages.framework.monitor.Monitor
import com.whitepages.framework.monitor.Monitor._
import com.whitepages.framework.exceptions.BadInputException
import com.whitepages.framework.util.ClassSupport
import spray.can.server.Stats
import com.whitepages.framework.exceptions.BadInputException

private[server] case class AdminHandler(context: ActorContext, dyn: Dynamic, buildInfo: Json) extends ClassSupport {

  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private def statsPresentation(s: Stats) = {
    JsonObject(
      "uptime" -> durationDHM(s.uptime),
      "totalRequests" -> s.totalRequests,
      "openRequests" -> s.openRequests,
      "maxOpenRequests" -> s.maxOpenRequests,
      "totalConnections" -> s.totalConnections,
      "openConnections" -> s.openConnections,
      "maxOpenConnections" -> s.maxOpenConnections,
      "requestTimeouts" -> s.requestTimeouts)
  }

  private def durationDHM(dur: FiniteDuration) = {
    val d = dur.toDays
    val h = dur.toHours
    val m = dur.toMinutes
    d + ":" + (h - 24 * d) + ":" + (m - 60 * (h + 24 * h))
  }

  def doGet(uri: String, httpListener: ActorRef): Future[(String, Any)] = {
    implicit val timeout: Timeout = 5.seconds
    uri match {
      case "/admin:ping" =>
        Future {
          ("text", "OK")
        }
      case "/admin:info" =>
        Future {
          ("json", buildInfo)
        }
      case "/admin:stats" =>
        val f = httpListener ? Http.GetStats
        f map {
          case x: Stats =>
            ("json", statsPresentation(x))
          case x => throw new Exception("Bad stats result: " + x)
        }
      case "/admin:clearStats" =>
        val f = httpListener ! Http.ClearStats
        Future {
          ("json", "Stats cleared")
        }
      case "/admin:monitorReset" =>
        monitor ? Reset map {
          case s: String => ("json", s"Monitor reset: $s")
        }
      case "/admin:monitorQueue" =>
        monitor ? ServerQueueSize map {
          case j: Json => ("json", j)
        }
      case "/admin:monitorGet" =>
        monitor ? GetAllMetrics map {
          case j: Json => ("json", j)
        }
      case "/admin:getAlert" =>
        monitor ? GetAlert map {
          case j: Json => ("json", j)
        }
      case "/admin:getConfig" =>
        Future {
          ("text", context.system.settings.config.root().render())
        }
      case "/admin:getProps" =>
        Future {
          val props = System.getProperties
          val j = (props map {
            case prop => prop
          }).toMap
          ("json", j)
        }
      case "/admin:toggleTest" =>
        monitor ? ToggleTestMetric map {
          case b: Boolean => ("json", b)
        }
      case "/admin:getDyn" =>
        val j = dyn.get(emptyJsonObject)
        Future {
          ("json", j)
        }
      case x => Future {
        throw new BadInputException("Bad admin command: " + x)
      }
    }
  }

  def doPost(uri: String, body: String): Future[(String, Any)] = {
    uri match {
      case "/admin:echo" =>
        Future {
          ("json", Json(body))
        }
      case x => Future {
        throw new BadInputException("Bad admin post command: " + x)
      }
    }
  }


  def doPut(uri: String, body: String): Future[(String, Any)] = {
    uri match {
      case "/admin:setDyn" =>
        val dyns = try {
          val j = Json(body)
          jgetObject(j)
        } catch {
          case ex: Throwable => emptyJsonObject
        }
        dyn.set(dyns)
        Future {
          ("text", "OK")
        }
      case x => Future {
        throw new BadInputException("Bad admin put command: " + x)
      }
    }
  }
}
