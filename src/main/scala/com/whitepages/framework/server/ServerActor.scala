package com.whitepages.framework.server

import scala.concurrent.duration._
import scala.concurrent.{Promise, Future, ExecutionContext}
import akka.actor._
import spray.can.Http
import spray.http._
import HttpMethods._
import MediaTypes._
import com.persist.JsonOps._
import com.whitepages.framework.util.{CheckedActor, Thrift}
import Thrift._
import akka.io.IO
import com.persist.Exceptions.SystemException
import java.util.UUID
import org.joda.time.format.ISODateTimeFormat
import java.net.URLDecoder
import com.whitepages.framework.monitor.Monitor
import Monitor.ServerError
import com.whitepages.framework.service._
import spray.http.HttpResponse
import spray.http.HttpRequest
import akka.actor.DeadLetter
import com.twitter.scrooge.{ThriftStruct, Info}
import com.whitepages.framework.exceptions._
import com.whitepages.framework.logging.{DurationStrings, noId, RequestId, LoggingControl}
import scala.language.postfixOps


private[server] object ServerActor {

  private def getServerOptions(query: spray.http.Uri.Query, prefix: String): Map[String, Any] = {

    val validMap = query.toMultiMap.filterKeys {
      k => k.startsWith(prefix)
    }
    validMap.map {
      case (k, v) => {
        val opt = k.substring(prefix.length).toLowerCase
        val parsedV = v match {
          case v1 :: Nil if v.contains(",") => v1.split(",").map(s => convertParam(s)).toList
          case v2 :: Nil => convertParam(v2)
          case v: List[String] => v.map(s => convertParam(s))
        }
        opt -> parsedV
      }
    }
  }

  def getServerOpts(query: spray.http.Uri.Query): Map[String, Any] = getServerOptions(query, "opt:")

  def getServerDyns(query: spray.http.Uri.Query): Map[String, Any] = getServerOptions(query, "dyn:")

  def convertParam(v1: String): Json = {
    val v = URLDecoder.decode(v1, "UTF8")
    v match {
      case jString if jString.startsWith("(") => {
        Json(jString.substring(1, jString.length - 1))
      }
      case x => x
    }
  }

  def stringifyJson(json: Json, opts: Map[String, Any]) = {
    if (opts.contains("pretty")) Pretty(json) else Compact(json)
  }

  def queryToJsonMap(query: spray.http.Uri.Query): Map[String, Json] = {
    query.toMultiMap.filterKeys {
      case x =>
        !x.startsWith("opt:") && !x.startsWith("dyn:")
    }.map {
      case (k, v :: Nil) => (k, convertParam(v))
      case (k, v: List[String]) => (k, v.map(p => convertParam(p)).reverse)
    }
  }

  case class Bind(listen: String, port: Integer)

  case object Unbind

  case object Drain

}

private[server] class ServerActor(private[this] val sd: BaseService,
                                  private[this] val bindCompletedPromise: Promise[Boolean],
                                  private[this] val unbindCompletedPromise: Promise[Boolean],
                                  private[this] val infos: Map[String, Info],
                                  private[this] val handler: BaseHandler,
                                  private[this] val queryStringHandler: (JsonObject, String) => JsonObject,
                                  private[this] val isDev: Boolean,
                                  private[this] val logRequest: Boolean,
                                  private[this] val logResponse: Boolean,
                                  private[this] val buildInfo: Json
                                   ) extends CheckedActor {

  import ServerActor._

  // TODO: extract constant
  private[this] implicit val timeout: akka.util.Timeout = 1 minute
  private[this] implicit val ec: ExecutionContext = context.dispatcher
  private[this] var isBound = false
  private[this] var httpListener: ActorRef = null
  private[this] val jsonHandler = handler match {
    case h: ThriftService.Handler => JsonHandler1(context, h, infos)
    case j: JsonService.Handler => JJsonHandler(context, j)
  }
  private[this] val thriftHandler = handler match {
    case h: ThriftService.Handler => ThriftHandler1(context, h, infos)
    case j: JsonService.Handler => null
  }
  private[this] val dynamic = Dynamic(isDev)
  private[this] val adminHandler = AdminHandler(context, dynamic, buildInfo)
  private[this] val logFmt = ISODateTimeFormat.dateHourMinuteSecondFraction()
  private[this] var draining:Boolean = false

  context.system.eventStream.subscribe(self, classOf[DeadLetter])

  def rec = {

    case req@HttpRequest(method, Uri.Path(uri), headers, entity, protocol) =>
      val t1 = System.nanoTime()
      val opts = getServerOpts(req.uri.query)
      val dyns = getServerDyns(req.uri.query)
      val logString = jgetString(dyns, "log")
      val logInt = LoggingControl.levelStringToInt(logString)
      val id = {
        def trackingId = headers.filter(_.name.toLowerCase == "x-tracking_id").map(_.value).
          headOption.getOrElse("")
        def spanId =
          headers.filter(_.name.toLowerCase == "x-span_id").map(_.value).headOption match {
            case Some(s) => s
            case None =>
              headers.filter(_.name.toLowerCase == "x-seq_id").map(_.value).headOption match {
                case Some(s) => s
                case None => "0"
              }

          }
        val (trackingId1, spanId1) = if (trackingId == "") {
          (UUID.randomUUID().toString, "0")
        } else {
          (trackingId, spanId)
        }
        RequestId(trackingId1, spanId1, level = logInt)
      }
      time.start(id, "")
      val clientIP = req.headers.filter(_.name == "x-cluster-client-ip").map(_.value).headOption orElse
        req.headers.filter(_.name == "x-forwarded-for").map(_.value).headOption orElse
        req.headers.filter(_.name == "Remote-Address").map(_.value).headOption getOrElse
        ""
      val ctype = headers.filter(_.name.toLowerCase == "content-type").map(_.value).headOption.getOrElse(`application/json`.value)
      val sender1 = sender
      val f: Future[(String, Json, Json, JsonObject)] = method match {
        case POST =>
          if (uri.startsWith("/admin:")) {
            adminHandler.doPost(uri, entity.data.asString(HttpCharsets.`UTF-8`)) map {
              case ("json", msg: Json) =>
                sender1 ! HttpResponse(entity = HttpEntity(`application/json`, stringifyJson(msg, opts)))
                (uri, jnull, jnull, emptyJsonObject)
              case (_, msg: String) =>
                sender1 ! HttpResponse(entity = HttpEntity(`text/plain`, msg))
                (uri, jnull, jnull, emptyJsonObject)
            }

          } else {
            if (draining) {
              log.error(noId, "POST occured while draining")
              throw NotAvailableException("draining")
            }
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
                    sender1 ! HttpResponse(entity = HttpEntity(`application/json`, responses))
                    (cmd1, requestj, responsej, extraj)
                }
              case thriftType.value if infos != null =>
                val f = thriftHandler.doThrift(id, entity.data.toByteArray, dynamic.get(dyns))
                f.map {
                  case (cmd1, responseBytes, requestj, responsej, extraj) =>
                    sender1 ! HttpResponse(entity = HttpEntity(thriftType, responseBytes))
                    (cmd1, requestj, responsej, extraj)
                }
              case _ =>
                Future {
                  monitor ! ServerError
                  val msg = s"Bad content type: ${ctype}"
                  throw new BadInputException(msg)
                }
            }
          }
        case GET =>
          if (uri == "/" + "favicon.ico") {
            Future {
              sender ! HttpResponse(status = 404)
              ("", jnull, jnull, emptyJsonObject)
            }
          } else if (uri.startsWith("/admin:")) {
            adminHandler.doGet(uri, httpListener) map {
              case ("json", msg: Json) =>
                sender1 ! HttpResponse(entity = HttpEntity(`application/json`, stringifyJson(msg, opts)))
                (uri, jnull, jnull, emptyJsonObject)
              case (_, msg: String) =>
                sender1 ! HttpResponse(entity = HttpEntity(`text/plain`, msg))
                (uri, jnull, jnull, emptyJsonObject)
            }
          } else {
            if (draining) {
              log.error(noId, "GET occured while draining")
              throw NotAvailableException("draining")
            }
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
                sender1 ! HttpResponse(entity = HttpEntity(`application/json`, responses))
                (cmd1, requestj, responsej, extraj)
            }
          }
        case PUT =>
          adminHandler.doPut(uri, entity.data.asString(HttpCharsets.`UTF-8`)) map {
            case ("json", msg: Json) =>
              sender1 ! HttpResponse(entity = HttpEntity(`application/json`, stringifyJson(msg, opts)))
              (uri, jnull, jnull, emptyJsonObject)
            case (_, msg: String) =>
              sender1 ! HttpResponse(entity = HttpEntity(`text/plain`, msg))
              (uri, jnull, jnull, emptyJsonObject)
          }

        case x =>
          Future {
            throw new BadInputException("Bad method: " + x)
          }
      }

      f onComplete {
        case x =>
          time.end(id, "")
      }

      f map {
        case (cmd, in, out, extras) if uri != "/admin:ping" =>
          val duration = (System.nanoTime() - t1) / 1000
          val j = JsonObject(
            "method" -> method.toString(),
            "@traceId" -> JsonArray(id.trackingId, id.spanId),
            "duration" -> DurationStrings.microsDuration(duration)) ++
            (if (clientIP != "") JsonObject("ip" -> clientIP) else emptyJsonObject) ++
            (if (logRequest && in != jnull) JsonObject("request" -> in) else emptyJsonObject) ++
            (if (logResponse && out != jnull) JsonObject("response" -> out) else emptyJsonObject) ++
            (if (extras != jnull && extras != emptyJsonObject) JsonObject("xtras" -> extras) else emptyJsonObject) ++
            (if (cmd != "") JsonObject("cmd" -> cmd) else emptyJsonObject)
          log.alternative("access", j)
      }
      f onFailure {
        case ex =>
          val (code, msg) = ex match {
            case ex: SystemException =>
              (400, JsonObject("kind" -> "JSON problem", "info" -> ex.info))
            case ex@BadInputException(msg) =>
              (400, JsonObject("kind" -> "Invalid client request", "msg" -> msg))
            case ex@NotAvailableException(msg) =>
              (503, JsonObject("kind" -> "Not available", "msg" -> msg))
            case ClientTimeoutException(msg) =>
              (503, JsonObject("kind" -> "Client timeout", "msg" -> msg))
            case ClientFailException(msg) =>
              (503, JsonObject("kind" -> "Client fail", "msg" -> msg))
            case FrameworkException(msg) =>
              (503, JsonObject("kind" -> "Unknown framework exception", "msg" -> msg))
            case ex =>
              (500, JsonObject("kind" -> "Exception", "msg" -> ex.toString))
          }
          sender1 ! HttpResponse(status = code, entity = Pretty(msg))
          if (code == 500) {
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
              "duration" -> DurationStrings.microsDuration(duration)
            )
          log.alternative("access", j)
      }

    case ServerActor.Drain => draining = true

    case d: DeadLetter =>
    // TODO deal with spray deadletters before reenabling this
    //log.warn(noId, s"DeadLetter sender:${d.sender} recipient:${d.recipient} msg:${d.message}")

    //
    // The next three messages are called before this server actor is bound to an
    // address. Once bound, this actor will receive Http.Connected message
    // for each new connection.
    //
    case ServerActor.Bind(listen, port) =>
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

    case ServerActor.Unbind =>
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


