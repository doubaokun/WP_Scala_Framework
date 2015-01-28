package com.whitepages.framework.client

import com.persist.JsonOps._
import spray.http._
import spray.http.ContentTypes._
import spray.http.HttpRequest
import spray.http.HttpResponse
import scala.language.postfixOps

private[client] case class JsonLoggingMapper(clientName: String) extends ClientHttpLoggingMapper {
  def inToJ(in: HttpRequest): JsonObject = {
    val cmd = in.uri.path.toString.split("""\?""").head
    val request = if (in.method == HttpMethods.GET) {
      in.uri.query.toMap
    } else {
      if (JsonLoggingMapper.hasJsonContentHeader(in.headers)) Json(in.entity.data.asString) else emptyJsonObject
    }

    JsonObject(
      "cmd" -> cmd,
      "request" -> request,
      "method" -> s"${in.method.name.toLowerCase}Json"
    )
  }

  def outToJ(out: HttpResponse) = {
    val body = if (JsonLoggingMapper.hasJsonContentHeader(out.headers)) Json(out.entity.data.asString) else emptyJsonObject
    val headers = out.headers.map { x => x.name -> x.value }.toMap
    JsonObject(
      "status" -> out.status.toString(),
      "headers" -> headers,
      "body" -> body
    )
  }

  def jToOut(j: Json) = {
    val body = jget(j, "body")
    val entity = Compact(body)
    val httpEntity = HttpEntity(`application/json`, HttpData(entity))
    val headers = List(HttpHeaders.`Content-Length`(httpEntity.data.toByteArray.length.toLong),
      HttpHeaders.`Content-Type`(`application/json`),
      HttpHeaders.`Date`(spray.http.DateTime.now))
    HttpResponse(entity = httpEntity, headers = headers)
  }

}

object JsonLoggingMapper {
  def hasJsonContentHeader(headers: List[HttpHeader]): Boolean = {
    val contentHeaderOpt = headers.filter(_.lowercaseName == "content-type").map(_.value).headOption
    !contentHeaderOpt.isDefined || contentHeaderOpt.get.contains(`application/json`.value)
  }
}
