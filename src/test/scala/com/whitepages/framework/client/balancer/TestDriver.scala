package com.whitepages.framework.client.balancer

import akka.actor.{Cancellable, Actor, PoisonPill, ActorRef}
import scala.language.postfixOps
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.{ClassSupport, ActorSupport}
import com.persist.JsonOps._
import com.whitepages.framework.client.DriverMessages
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


object TestDriver extends ClassSupport {

  implicit val ex: ExecutionContext = system.dispatcher

  def delay(d: FiniteDuration)(body: => Unit) {
    system.scheduler.scheduleOnce(d) {
      body
    }
  }

  trait Mode

  case object Normal extends Mode

  case object ConnectFail extends Mode

  case object Zombie extends Mode

  case class PlanEvent(mode: Mode, offset: FiniteDuration)

  case class PlanTime(mode: Mode, time: Long)

  case class ChangeMode(mode: Mode)

  def fix(planEvents: Seq[PlanEvent]): Seq[PlanTime] = {
    val t = System.currentTimeMillis()
    planEvents map {
      case planEvent =>
        PlanTime(planEvent.mode, (t + planEvent.offset.toMillis))
    }
  }

}

private[client] class TestDriver(driverMessages: DriverMessages[Json, Json],
                                 host: String, port: Int,
                                 clientName: String,
                                 healthRequest: Json,
                                 plan: Seq[TestDriver.PlanTime]) extends Actor with ActorSupport {

  import driverMessages._

  import TestDriver._

  private[this] var remainPlan = plan
  private[this] var mode: Mode = Normal

  val t = System.currentTimeMillis()

  val planTimers = for (planTime <- plan) yield {
    if (planTime.time <= t) {
      mode = planTime.mode
      None
    } else {
      val delta = (planTime.time - t).milliseconds
      Some(system.scheduler.scheduleOnce(delta) {
        self ! ChangeMode(planTime.mode)
      })
    }
  }
  log.info(noId, JsonObject("client" -> clientName, "INITIAL-MODE" -> mode.toString))


  private[this] val balancer: ActorRef = context.parent

  private[this] val config = system.settings.config
  private[this] val cpath = Seq("wp", serviceName, "clients", clientName)

  private[this] val multiPath = (cpath :+ "multi").mkString(".")
  private[this] val multi = if (config.hasPath(multiPath)) {
    config.getBoolean(multiPath)
  } else {
    false
  }
  private[this] val ddPath = (cpath :+ "debugDriver").mkString(".")
  private[this] val debug = if (config.hasPath(ddPath)) {
    config.getBoolean(ddPath)
  } else {
    false
  }
  if (debug) {
    log.info(noId, JsonObject("client" -> clientName, "TEST-DRIVER-MSG" -> "OPEN CONNECTION"))
  }

  delay(1 second) {
    if (mode == ConnectFail) {
      balancer ! DriverConnectFailed("test")
      self ! PoisonPill
    } else if (mode == Zombie) {
    } else {
      balancer ! DriverReady
    }
  }

  def receive: PartialFunction[Any, Unit] = {
    case ChangeMode(mode0) =>
      mode = mode0
      log.info(noId, JsonObject("client" -> clientName, "CHANGE-MODE" -> mode.toString))

    case DriverClose =>
      planTimers map {
        case to => to map {
          case t => t.cancel()
        }
      }
      delay(1 second) {
        balancer ! DriverClosed
        //balancer ! DriverFail("HTTP error close")
        self ! PoisonPill
      }
    case x: Any =>
      if (debug) {
        log.info(noId, JsonObject("client" -> clientName, "TEST-DRIVER-MSG" -> x.toString))
      }
      if (mode != Zombie)
        receive1(x)
  }

  def receive1: PartialFunction[Any, Unit] = {
    case DriverSend(in, id, uid) =>
      delay(50 milliseconds) {
        balancer ! DriverAck
        balancer ! DriverReceive(in, uid)
        //balancer ! DriverReceiveFail("Client bad request", uid)
        //balancer ! DriverReceiveRetry(s"Bad http response code: ${status.toString()}", uid)
      }
    case DriverHealthCheck(uid) =>
      delay(50 milliseconds) {
        val ok = true
        balancer ! DriverAck
        balancer ! DriverHealthResult(ok, uid)
      }

    case x: Any =>
      val c = x.getClass.toString
      log.error(noId, JsonObject("client" -> clientName, "msg" -> "Unexpected test actor message",
        "path" -> self.path.toString, "class" -> c, "exmsg" -> x.toString))
  }

}
