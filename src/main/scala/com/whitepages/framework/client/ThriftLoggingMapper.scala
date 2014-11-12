package com.whitepages.framework.client

import com.persist.JsonOps._
import com.twitter.scrooge.serialization.ThriftCodec
import spray.http._
import com.whitepages.framework.util.Thrift
import Thrift._
import org.apache.thrift.protocol.TMessage
import com.twitter.scrooge.{Info, ThriftStruct}
import spray.http.HttpRequest
import spray.http.HttpResponse
import com.whitepages.framework.util.Thrift
import com.whitepages.framework.util

private[client] case class ThriftLoggingMapper[IN <: ThriftStruct, OUT <: ThriftStruct](info: Info, thriftProtocol: ThriftProtocol, clientName: String)
                                                                                       (implicit val inCodec: ThriftCodec[IN], val outCodec: ThriftCodec[OUT]) extends ClientHttpLoggingMapper {
  def inToJ(request: HttpRequest): JsonObject = {
    val reqBytes = request.entity.data.toByteArray
    val (thriftReqArgs, tMsg) = util.Thrift.deserializeThrift[IN](reqBytes, thriftProtocol)
    val jReqArgs = info.in.writeCodec.write(thriftReqArgs)

    JsonObject(
      "cmd" -> tMsg.name,
      "request" -> jReqArgs,
      "method" -> "postThrift",
      "client" -> clientName
    )
  }

  def outToJ(out: HttpResponse): Json = out match {
    case HttpResponse(status, entity, headers, protocol) =>
      val resultBytes = entity.data.toByteArray
      val (thriftResp, tMsg) = util.Thrift.deserializeThrift[OUT](resultBytes, thriftProtocol)

      JsonObject(
        "cmd" -> tMsg.name,
        "body" -> info.out.writeCodec.write(thriftResp)
      )
  }

  def jToOut(j: Json): HttpResponse = {
    val cmd = jget(j, "cmd").asInstanceOf[String]
    val thriftResp = info.out.readCodec.read(jget(j, "body"))
    val msg = new TMessage(cmd, 0, 0)
    val bytes = util.Thrift.serializeThrift(thriftResp, thriftProtocol, msg)
    val headers = List(HttpHeaders.`Content-Length`(bytes.length.toLong),
      HttpHeaders.`Content-Type`(thriftType),
      HttpHeaders.`Date`(spray.http.DateTime.now))
    HttpResponse(entity = HttpEntity(Thrift.thriftType, bytes), headers = headers)
  }
}

