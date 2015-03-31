package com.whitepages.framework.server

import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.concurrent.{Promise, Future, ExecutionContext}
import akka.actor._
import spray.can.Http
import spray.http._
import HttpMethods._
import MediaTypes._
import com.persist.JsonOps._
import com.whitepages.framework.util.CheckedActor
import akka.io.IO
import com.persist.Exceptions.SystemException
import java.util.UUID
import org.joda.time.format.ISODateTimeFormat
import com.whitepages.framework.service._
import spray.http.HttpResponse
import spray.http.HttpRequest
import akka.actor.DeadLetter
import com.whitepages.framework.exceptions._
import com.whitepages.framework.logging.{LoggingLevels, noId, RequestId}
import scala.language.postfixOps
import com.whitepages.framework.service.SprayService
import SprayService.SprayIn
import SprayService.SprayOut

private[server] class SprayServerActor(private[this] val sd: BaseService,
                                       private[this] val serviceConfig: Config,
                                       private[this] val bindCompletedPromise: Promise[Boolean],
                                       private[this] val unbindCompletedPromise: Promise[Boolean],
                                       private[this] val service: SprayService,
                                       private[this] val isDev: Boolean,
                                       private[this] val buildInfo: Json
                                        ) extends CheckedActor {

  import SprayUtil._

  private[this] val logRequest = serviceConfig.getBoolean("logRequest")
  private[this] val logResponse = serviceConfig.getBoolean("logResponse")
  private[this] val doAdmin = serviceConfig.getBoolean("doAdmin")

  // TODO: extract constant
  private[this] implicit val timeout: akka.util.Timeout = 1 minute
  private[this] implicit val ec: ExecutionContext = context.dispatcher
  private[this] var isBound = false
  private[this] var httpListener: ActorRef = null

  private[this] val dynamic = Dynamic(isDev)
  private[this] val adminHandler = AdminHandler(context, dynamic, buildInfo)
  private[this] val logFmt = ISODateTimeFormat.dateHourMinuteSecondFraction()
  private[this] var draining: Boolean = false

  context.system.eventStream.subscribe(self, classOf[DeadLetter])

  /*
  def handleThrift(sender: ActorRef, req: HttpRequest, opts: Map[String, Any]): Future[Option[Result]] = {
    val HttpRequest(method, Uri.Path(uri), headers, entity, protocol) = req
    method match {
      case GET =>
        val parts = uri.split("/")
        val cmd = if (parts.size < 2) {
          ""
        } else if (thriftHandler == null) {
          parts.tail.mkString("/").split("[?]")(0)
        } else {
          parts(1).split("[?]")(0)
        }
        val jsonMap = queryToJsonMap(req.uri.query)
        val f = jsonHandler.doJson(id, cmd, queryStringHandler(jsonMap, cmd), dynamic.get(dyns), "GET")
        f.map {
          case (cmd1, requestj, responsej, extraj) =>
            val responses = opts.get("pretty") match {
              case Some(_) => Pretty(responsej)
              case None => Compact(responsej)
            }
            sender ! HttpResponse(entity = HttpEntity(`application/json`, responses))
            (cmd1, requestj, responsej, extraj)
        }
      case POST =>
        val ctype = headers.filter(_.name.toLowerCase == "content-type").map(_.value).headOption.getOrElse(`application/json`.value)
        ctype match {
          case `application/json`.value =>
            val parts = uri.split("/")
            val cmd = if (parts.size < 2) {
              ""
            } else if (thriftHandler == null) {
              parts.tail.mkString("/").split("[?]")(0)
            } else {
              parts(1).split("[?]")(0)
            }
            val f = jsonHandler.doJsonString(id, cmd, entity.data.asString(HttpCharsets.`UTF-8`), dynamic.get(dyns), "POST")
            f.map {
              case (cmd1, requestj, responsej, extraj) =>
                val responses = opts.get("pretty") match {
                  case Some(_) => Pretty(responsej)
                  case None => Compact(responsej)
                }
                sender ! HttpResponse(entity = HttpEntity(`application/json`, responses))
                (cmd1, requestj, responsej, extraj)
            }
          case thriftType.value if infos != null =>
            val f = thriftHandler.doThrift(id, entity.data.toByteArray, dynamic.get(dyns))
            f.map {
              case (cmd1, responseBytes, requestj, responsej, extraj) =>
                sender ! HttpResponse(entity = HttpEntity(thriftType, responseBytes))
                (cmd1, requestj, responsej, extraj)
            }
          case _ =>
            Future {
              monitor ! ServerError
              val msg = s"Bad content type: ${ctype}"
              throw new BadInputException(msg)
            }
        }
      case _ =>
        Future.successful(None)
    }
  }
  */

  def handleSpecialRequests(in: SprayIn): Future[Option[SprayOut]] = {
    val HttpRequest(method, Uri.Path(uri), headers, entity, protocol) = in.request
    method match {
      case GET =>
        if (uri == "/" + "favicon.ico") {
          Future {
            val response = HttpResponse(status = 404)
            Some(SprayOut(response, "", jnull, jnull, emptyJsonObject))
          }
        } else if (doAdmin && uri.startsWith("/admin:")) {
          adminHandler.doGet(uri, httpListener) map {
            case ("json", msg: Json) =>
              val response = HttpResponse(entity = HttpEntity(`application/json`, stringifyJson(msg, in.opts)))
              Some(SprayOut(response, uri, jnull, jnull, emptyJsonObject))
            case (_, msg: String) =>
              val response = HttpResponse(entity = HttpEntity(`text/plain`, msg))
              Some(SprayOut(response, uri, jnull, jnull, emptyJsonObject))
            case _ =>
              None
          }
        } else {
          Future.successful(None)
        }
      case POST =>
        if (doAdmin && uri.startsWith("/admin:")) {
          adminHandler.doPost(uri, entity.data.asString(HttpCharsets.`UTF-8`)) map {
            case ("json", msg: Json) =>
              val response = HttpResponse(entity = HttpEntity(`application/json`, stringifyJson(msg, in.opts)))
              Some(SprayOut(response, uri, jnull, jnull, emptyJsonObject))
            case (_, msg: String) =>
              val response = HttpResponse(entity = HttpEntity(`text/plain`, msg))
              Some(SprayOut(response, uri, jnull, jnull, emptyJsonObject))
            case _ =>
              None
          }
        } else {
          Future.successful(None)
        }
      case PUT =>
        if (doAdmin && uri.startsWith("/admin:")) {
          adminHandler.doPut(uri, entity.data.asString(HttpCharsets.`UTF-8`)) map {
            case ("json", msg: Json) =>
              val response = HttpResponse(entity = HttpEntity(`application/json`, stringifyJson(msg, in.opts)))
              Some(SprayOut(response, uri, jnull, jnull, emptyJsonObject))
            case (_, msg: String) =>
              val response = HttpResponse(entity = HttpEntity(`text/plain`, msg))
              Some(SprayOut(response, uri, jnull, jnull, emptyJsonObject))
            case _ =>
              None
          }
        } else {
          Future.successful(None)
        }
      case _ =>
        Future.successful(None)
    }
  }

  def rec = {

    case req@HttpRequest(method, Uri.Path(uri), headers, entity, protocol) =>
      val t1 = System.nanoTime()
      val opts = getServerOpts(req.uri.query)
      val dyns = getServerDyns(req.uri.query)
      val logString = jgetString(dyns, "log")
      val logInt = LoggingLevels.levelStringToInt(logString)

      def getHeaderOption(lcName: String) = headers.filter(_.is(lcName)).map(_.value).headOption
      def getHeader(lcName: String, default: String = "") = getHeaderOption(lcName) getOrElse default
      def getFirstHeader(lcHeaderNames: Seq[String], default: String = "") = {
        lcHeaderNames.foldLeft[Option[String]](None)(_ orElse getHeaderOption(_)) getOrElse default
      }

      val id = {
        val trackingIdHeader = getHeader("x-tracking_id")
        if (trackingIdHeader == "") {
          RequestId(UUID.randomUUID().toString, "0", level = logInt)
        } else {
          RequestId(trackingIdHeader, getFirstHeader(Seq("x-span_id","x-seq_id"),"0"), level = logInt)
        }
      }

      time.start(id, "")
      val clientIP = getFirstHeader(Seq("x-cluster-client-ip","x-forwarded-for","remote-address"))
      val ctype = getHeader("content-type", `application/json`.value)
      val sender1 = sender

      val in = SprayIn(req, dyns, opts, id)
      val fstep1 = handleSpecialRequests(in)
      val fstep2 = fstep1 map {
        case Some(x) =>
          Some(x)
        case None if (draining) =>
          log.error(noId, "POST occurred while draining")
          throw NotAvailableException("draining")
        case _ =>
          None
      }
      val f = fstep2 flatMap {
        case Some(x) => Future.successful(Some(x))
        case None => service.sprayAct(in)
      }

      f onComplete {
        case x =>
          time.end(id, "")
      }

      f map {
        case Some(SprayOut(response, cmd, in, out, extras)) if uri == "/admin:ping" =>
          sender1 ! response
        case Some(SprayOut(response, cmd, in, out, extras)) =>
          sender1 ! response
          val duration = (System.nanoTime() - t1) / 1000
          val j = JsonObject(
            "method" -> method.toString(),
            "@traceId" -> JsonArray(id.trackingId, id.spanId),
            "duration" -> duration) ++
            (if (clientIP != "") JsonObject("ip" -> clientIP) else emptyJsonObject) ++
            (if (logRequest && in != jnull) JsonObject("request" -> in) else emptyJsonObject) ++
            (if (logResponse && out != jnull) JsonObject("response" -> out) else emptyJsonObject) ++
            (if (extras != jnull && extras != emptyJsonObject) JsonObject("xtras" -> extras) else emptyJsonObject) ++
            (if (cmd != "") JsonObject("cmd" -> cmd) else emptyJsonObject)
          log.alternative("access", j)
        case None => throw new BadInputException("Unrecognized request")
      }
      f onFailure {
        case ex =>
          val (code, msg) = ex match {
            case ex: SystemException =>
              (StatusCodes.BadRequest, JsonObject("kind" -> "JSON problem", "info" -> ex.info)) // 400
            case ex@BadInputException(msg) =>
              (StatusCodes.BadRequest, JsonObject("kind" -> "Invalid client request", "msg" -> msg)) // 400
            case ex@TooManyRequestsException(msg) =>
              (StatusCodes.TooManyRequests, JsonObject("kind" -> "Back-off, man", "msg" -> msg)) // 429
            case ex@NotAvailableException(msg) =>
              (StatusCodes.ServiceUnavailable, JsonObject("kind" -> "Not available", "msg" -> msg)) // 503
            case ClientTimeoutException(msg) =>
              (StatusCodes.ServiceUnavailable, JsonObject("kind" -> "Client timeout", "msg" -> msg)) // 503
            case ClientFailException(msg) =>
              (StatusCodes.ServiceUnavailable, JsonObject("kind" -> "Client fail", "msg" -> msg)) // 503
            case FrameworkException(msg) =>
              (StatusCodes.ServiceUnavailable, JsonObject("kind" -> "Unknown framework exception", "msg" -> msg)) // 503
            case ex =>
              (StatusCodes.InternalServerError, JsonObject("kind" -> "Exception", "msg" -> ex.toString)) // 500
          }
          sender1 ! HttpResponse(status = code, entity = Compact(msg))
          if (code.intValue >= StatusCodes.InternalServerError.intValue) {
            log.error(id, msg, ex)
          } else {
            log.warn(id, msg)
          }
          val duration = (System.nanoTime() - t1) / 1000
          val j = (if (clientIP != "") JsonObject("ip" -> clientIP) else emptyJsonObject) ++
            (if (method == POST && ctype == `application/json`.value) JsonObject("postbody" -> entity.data.asString(HttpCharsets.`UTF-8`)) else emptyJsonObject) ++
            JsonObject(
              "method" -> method.toString(),
              "uri" -> uri, "XCODE" -> code,
              "XERROR" -> msg,
              "@traceId" -> JsonArray(id.trackingId, id.spanId),
              "duration" -> duration
            )
          log.alternative("access", j)
      }

    case SprayUtil.Drain => draining = true

    case d: DeadLetter =>
    // TODO deal with spray deadletters before reenabling this
    //log.warn(noId, s"DeadLetter sender:${d.sender} recipient:${d.recipient} msg:${d.message}")

    //
    // The next three messages are called before this server actor is bound to an
    // address. Once bound, this actor will receive Http.Connected message
    // for each new connection.
    //
    case SprayUtil.Bind(listen, port) =>
      if (isBound) {
        log.error(noId, JsonObject("kind" -> "Server is already bound, cannot bind", "host" -> listen, "port" -> port))
      } else {
        log.info(noId, JsonObject("kind" -> "Binding", "host" -> listen, "port" -> port))
        IO(Http)(context.system) ! Http.Bind(self, interface = listen, port = port)
      }

    case Http.Bound(addr) =>
      log.debug(noId, JsonObject("kind" -> "Listening", "to" -> addr.toString, "for" -> sender.path.toString))
      isBound = true
      httpListener = sender
      bindCompletedPromise success (true)

    case Http.CommandFailed(Http.Bind(_, addr, _, _, _)) =>
      log.error(noId, JsonObject("kind" -> "Failed to bind", "to" -> addr.toString))
      bindCompletedPromise success (false)

    //
    // The next message is called once for every incoming request, and registers which
    // actor will handle the message (in our case, ourselves)
    //

    case _: Http.Connected => sender ! Http.Register(self)

    //
    // The next three messages are used when we are unbinding for our bound addresses
    //

    case SprayUtil.Unbind =>
      if (isBound) {
        log.debug(noId, "Unbinding listener")
        httpListener ! Http.Unbind
      } else {
        // We were not bound, let's immediately signal that unbinding succeeded
        unbindCompletedPromise.success(true)
      }

    case Http.Unbound =>
      log.debug(noId, "Finished listening")
      isBound = false
      httpListener = null
      unbindCompletedPromise.success(true)

    case Http.CommandFailed(Http.Unbound) =>
      log.error(noId, "Failed to unbind")
      unbindCompletedPromise.success(false)

    case x =>
  }

}


