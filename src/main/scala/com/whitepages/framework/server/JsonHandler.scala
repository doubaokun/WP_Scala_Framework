package com.whitepages.framework.server

/*
import akka.actor.ActorContext
import com.persist.JsonOps._
import scala.concurrent.{ExecutionContext, Future}
import com.twitter.scrooge.Info
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.monitor.Monitor
import Monitor.{JsonError, JsonTime, JsonCommand}
import com.whitepages.framework.service.ThriftService._
import com.whitepages.framework.exceptions.BadInputException
import com.whitepages.framework.logging.RequestId
import com.whitepages.framework.util.Thrift._

private[server] case class JsonHandler1(context: ActorContext, handler: Handler, infos: Map[String,Info])
  extends BaseJsonHandler with ClassSupport {

  private[this] implicit val ec: ExecutionContext = context.dispatcher

  def doJson(id: RequestId,
             cmd: String,
             jrequest: => Json,
             dyn: JsonObject,
             method:String): Future[(String, Json, Json, JsonObject)] = {
    time.start(id, "DOJSON")
    val cmdCamelCase = snakeToCamel(cmd)
    val t1 = System.nanoTime()
    val f1 = Future {
      monitor ! JsonCommand(cmdCamelCase)
      val requestj = jrequest
      val info = infos.get(cmdCamelCase) match {
        case Some(i) => i
        case None => throw new BadInputException(s"Unrecognized json command: ${cmd} (no method ${cmdCamelCase})")
      }
      val requestObj =
        Time(id,"TOOBJECT")(info.in.readCodec.read(requestj))
      (info, requestj, requestObj)
    }
    val f2 = f1 flatMap {
      case (minfo, requestj, requestObj) =>
        handler.act(Request(cmdCamelCase, requestObj, id, dyn)) map {
          case response =>
            val responsej = infos(cmdCamelCase).out.writeCodec.write(response.response)
            time.end(id, "DOJSON")
            monitor ! JsonTime(cmdCamelCase, System.nanoTime() - t1, response.monitor)
            (cmdCamelCase, requestj, responsej, response.logItems)
        }
    }
    f2.onFailure {
      case ex =>
        time.end(id, "DOJSON")
        monitor ! JsonError
    }
    f2
  }

  def doJsonString(id: RequestId, cmd: String, requests: String, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)] = {
    doJson(id, cmd, Json(requests), dyn, method)
  }

}
*/
