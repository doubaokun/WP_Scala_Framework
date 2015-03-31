package com.whitepages.framework.server

import akka.actor.ActorRefFactory
import com.persist.JsonOps._
import com.whitepages.framework.service.JsonService
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import spray.http._
import scala.concurrent.{ExecutionContext, Future}
import com.whitepages.framework.monitor.Monitor
import Monitor.{JsonError, JsonTime, JsonCommand}
import com.whitepages.framework.service.JsonService._
import com.whitepages.framework.logging.RequestId
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.service.SprayService.SprayIn
import com.whitepages.framework.service.SprayService.SprayOut

private[framework] case class JsonAct(context: ActorRefFactory, service:JsonService, handler: Handler) extends ClassSupport {
  private[this] val queryStringHandler = service.queryStringHandler.getOrElse(DefaultQueryStringHandler.handle)

  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private def doJson(id: RequestId, cmd: String, requestj: => Json, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)] = {
    val t1 = System.nanoTime()
    val f1 = Future {
      monitor ! JsonCommand("")
      handler.act(Request(cmd, requestj, id, dyn, method))
    }
    val f1a = f1 flatMap {
      case x => x
    }
    val f2 = f1a.map {
      case response =>
        val responsej = response.response
        monitor ! JsonTime("", System.nanoTime() - t1, response.monitor)
        (cmd, requestj, responsej, response.logItems)
    }
    f2.onFailure {
      case ex => monitor ! JsonError
    }
    f2
  }

  private def doJsonString(id: RequestId, cmd: String, requests: String, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)] = {
    doJson(id, cmd, Json(requests), dyn, method)
  }

  def sprayAct(in: SprayIn): Future[Option[SprayOut]] = {

    val req@HttpRequest(method, Uri.Path(uri), headers, entity, protocol) = in.request
    method match {
      case GET =>
        val xcmd = uri.replaceFirst("/","")
        //val parts = uri.split("/")
        //val cmd = if (parts.size < 2) {
        //  ""
        //} else {
        //  parts(1).split("[?]")(0)
        //}
        val jsonMap = SprayUtil.queryToJsonMap(req.uri.query)
        val f = doJson(in.id, xcmd, queryStringHandler(jsonMap, xcmd), in.dyn, "GET")
        f.map {
          case (cmd1, requestj, responsej, extraj) =>
            val responses = in.opts.get("pretty") match {
              case Some(_) => Pretty(responsej)
              case None => Compact(responsej)
            }
            val response = HttpResponse(entity = HttpEntity(`application/json`, responses))
            Some(SprayOut(response, cmd1, requestj, responsej, extraj))
        }
      case m:HttpMethod =>
        val xcmd = uri.replaceFirst("/","")
        //val parts = uri.split("/")
        //val cmd = if (parts.size < 2) {
        //  ""
        //} else {
        //  parts(1).split("[?]")(0)
        //}
        val f = doJsonString(in.id, xcmd, entity.data.asString(HttpCharsets.`UTF-8`), in.dyn, m.toString())
        f.map {
          case (cmd1, requestj, responsej, extraj) =>
            val responses = in.opts.get("pretty") match {
              case Some(_) => Pretty(responsej)
              case None => Compact(responsej)
            }
            val response = HttpResponse(entity = HttpEntity(`application/json`, responses))
            Some(SprayOut(response, cmd1, requestj, responsej, extraj))
        }

      case _ =>
        Future.successful(None)
    }
  }

}

