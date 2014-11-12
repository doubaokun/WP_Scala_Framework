package com.whitepages.framework.server

import akka.actor.{ActorContext, ActorRef}
import com.persist.JsonOps._
import scala.concurrent.{ExecutionContext, Future}
import com.whitepages.framework.monitor.Monitor
import Monitor.{JsonError, JsonTime, JsonCommand}
import com.whitepages.framework.service.JsonService._
import com.whitepages.framework.logging.RequestId
import com.whitepages.framework.util.ClassSupport

private[server] case class JJsonHandler(context: ActorContext, handler: Handler)
  extends BaseJsonHandler with ClassSupport {

  private[this] implicit val ec: ExecutionContext = context.dispatcher

  def doJson(id: RequestId, cmd: String, requestj: => Json, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)] = {
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

  def doJsonString(id: RequestId, cmd: String, requests: String, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)] = {
    doJson(id, cmd, Json(requests), dyn, method)
  }

}

