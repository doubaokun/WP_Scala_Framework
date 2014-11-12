package com.whitepages.framework.server

import akka.actor.ActorContext
import scala.concurrent.{ExecutionContext, Future}
import com.twitter.scrooge.Info
import com.whitepages.framework.util.{ClassSupport, Thrift}
import Thrift._
import com.persist.JsonOps._
import com.whitepages.framework.exceptions.BadInputException
import com.whitepages.framework.logging.RequestId
import com.whitepages.framework.monitor.Monitor
import Monitor.{ThriftError, ThriftTime, ThriftCommand}
import com.whitepages.framework.service.ThriftService._

private[server] case class ThriftHandler1(context: ActorContext, handler: Handler, infos: Map[String, Info]) extends ClassSupport {

  private[this] implicit val ec: ExecutionContext = context.dispatcher
  private[this] val logRequest = config.getBoolean("wp.service.logRequest")
  private[this] val logResponse = config.getBoolean("wp.service.logResponse")

  def doThrift(id: RequestId, request: Array[Byte], dyn:JsonObject): Future[(String, Array[Byte], Json, Json, JsonObject)] = {
    val t1 = System.nanoTime()
    val f1 = Future {
      val(requestObj, msg) = deserializeThriftIn(request, ThriftBinaryProtocol, infos)

      // Thrift generator converts method names into camel case, so we
      // have to convert the incoming snake case into camel case here
      // We still log and monitor using the incoming (snake case)
      // name
      val cmd = msg.name
      val cmdCamelCase = snakeToCamel(cmd)
      monitor ! ThriftCommand(cmdCamelCase)
      val info = infos.get(cmdCamelCase) match {
        case Some(i) => i
        case None => throw new BadInputException(s"Unrecognized thrift command: ${cmd} (no method ${cmdCamelCase})")
      }
      val requestj = if (logRequest) info.in.writeCodec.write(requestObj) else jnull
      (msg, info, requestj, requestObj, cmdCamelCase)
    }
    val f2 = f1 flatMap {
      case (msg, info, requestj, requestObj, cmdCamelCase) =>
        handler.act(Request(cmdCamelCase, requestObj, id, dyn)) map {
          case response =>
            val responsej = if (logResponse) info.out.writeCodec.write(response.response) else jnull
            val resultBytes: Array[Byte] = serializeThrift(response.response, ThriftBinaryProtocol, msg = msg)
            monitor ! ThriftTime(cmdCamelCase, System.nanoTime() - t1, response.monitor)
            (cmdCamelCase, resultBytes, requestj, responsej, response.logItems)
        }
    }
    f2.onFailure {
      case ex => monitor ! ThriftError
    }
    f2
  }

}
