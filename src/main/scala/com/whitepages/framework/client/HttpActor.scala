package com.whitepages.framework.client

import spray.http.{StatusCodes, HttpResponse, HttpRequest}
import akka.io.IO
import spray.can.Http
import akka.actor.{Terminated, Actor, PoisonPill, ActorRef}
import scala.language.postfixOps
import scala.collection.mutable
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ActorSupport
import com.persist.JsonOps._

private[client] class HttpActor(driverMessages: DriverMessages[HttpRequest, HttpResponse],
                                host: String,
                                port: Int,
                                clientName: String,
                                healthRequest: HttpRequest) extends Actor with ActorSupport {

  import driverIds._
  import driverMessages._

  private[this] val balancer: ActorRef = context.parent
  private[this] var sprayActor: ActorRef = null

  //private[this] val cpath = Seq("wp", serviceName, "clients", clientName)
  private[this] val clientConfig = getClientConfig(clientName)

  private[this] val useSsl = clientConfig.getBoolean("useSsl")
  private[this] val multi = clientConfig.getBoolean("multi")
  private[this] val debug = clientConfig.getBoolean("debugDriver")
  private[this] val oneShot = clientConfig.getBoolean("oneShot")
  if (debug) {
    log.info(noId, JsonObject("client" -> clientName, "HTTP-DRIVER-MSG" -> "OPEN CONNECTION"))
  }
  IO(Http) ! Http.Connect(host, port, useSsl)

  case object SprayAck

  private[this] val q = mutable.Queue[AllId]()

  def receive: PartialFunction[Any, Unit] = {
    case x: Any =>
      if (debug) {
        //log.info(noId, s"HTTP Driver($clientName,${self.path}}) MSG: $x")
        log.info(noId, JsonObject("client" -> clientName, "HTTP-DRIVER-MSG" -> x.toString,
          "self" -> self.path.toString, "sender" -> sender.path.toString))
      }
      receive1(x)
  }

  def receive1: PartialFunction[Any, Unit] = {
    case Http.Connected(_, _) =>
      sprayActor = sender
      context.watch(sender)
      balancer ! DriverReady
    case Http.CommandFailed(cmd) =>
      val msg = cmd.failureMessage.toString()
      balancer ! DriverConnectFailed(msg)
      self ! PoisonPill
    case DriverSend(in, id, uid) =>
      q.enqueue(uid)
      if (multi) {
        sprayActor ! in.withAck(SprayAck)
      } else {
        sprayActor ! in
      }
    case DriverHealthCheck(uid) =>
      q.enqueue(uid)
      if (multi) {
        sprayActor ! healthRequest.withAck(SprayAck)
      } else {
        sprayActor ! healthRequest
      }
    case SprayAck =>
      // This only occurs if multi
      balancer ! DriverAck
    case response@HttpResponse(status, entity, _, _) =>
      val close = oneShot || !response.headers.find(h => (h.name.toLowerCase() == "connection" && h.value.toLowerCase().contains("close"))).isEmpty
      if (!multi && !close) balancer ! DriverAck
      if (debug && close) {
        log.info(noId, JsonObject("client" -> clientName, "HTTP-DRIVER-MSG" -> "CLOSE CONNECTION",
          "headers" -> response.headers.toString))
      }
      q.dequeue() match {
        case uid: ReqTryId =>
          if (200 <= status.intValue && status.intValue <= 299) {
            balancer ! DriverReceive(response, uid)
          } else if (status == StatusCodes.ServiceUnavailable) {
            if (!close) balancer ! DriverReceiveRetry("Not available", uid)
          } else {
            balancer ! DriverReceiveFail("Client bad request", uid,
              extra = Some(JsonObject("status" -> status.intValue, "entity" -> entity.toString)))
          }
        case uid: HealthId =>
          val ok = status == StatusCodes.OK
          if (!ok && debug) {
            log.warn(noId, JsonObject("client" -> clientName, "status" -> status.toString,
              "path" -> self.path.toString, "msg" -> "Bad health check"))
          }
          balancer ! DriverHealthResult(ok, uid)
      }
      if (close) {
        balancer ! DriverFail("HTTP Connection Close Header")
        sprayActor ! Http.Close
        self ! PoisonPill
      }

    case DriverClose =>
      if (sprayActor != null) sprayActor ! Http.Close
      sprayActor = null
    case Http.Closed =>
      balancer ! DriverClosed
      self ! PoisonPill

    case Http.PeerClosed =>
      balancer ! DriverFail("HTTP peer close")
      self ! PoisonPill
    case Http.ErrorClosed =>
      balancer ! DriverFail("HTTP error close")
      self ! PoisonPill
    case Terminated(child) =>
      balancer ! DriverFail("HTTP SPRAY client terminated: " + child.path.toString)
      self ! PoisonPill
    case spray.http.Timedout(request) =>
      balancer ! DriverFail("HTTP SPRAY client timedout: " + request.toString)
      self ! PoisonPill
    case x: Any =>
      val c = x.getClass.toString
      log.error(noId, JsonObject("client" -> clientName, "msg" -> "Unexpected HTTP actor message",
        "path" -> self.path.toString, "class" -> c, "exmsg" -> x.toString))
  }

}
