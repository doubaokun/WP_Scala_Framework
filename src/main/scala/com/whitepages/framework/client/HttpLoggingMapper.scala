package com.whitepages.framework.client

import com.persist.JsonOps._
import spray.http._
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.HttpHeaders.RawHeader
import scala.language.postfixOps
import com.whitepages.framework.util.ClassSupport

private[client] case class HttpLoggingMapper(clientName: String) extends ClientHttpLoggingMapper with ClassSupport {
  def inToJ(in: HttpRequest): JsonObject = {
    val cmd = in.uri.path.toString.split("""\?""").head
    val request = if (in.uri.query.isEmpty) jnull else in.uri.query.toMap
    val headers = in.headers.map { x => x.name -> x.value }.toMap

    JsonObject(
      "cmd" -> cmd,
      "headers" -> headers,
      "request" -> request,
      "method" -> s"${in.method.name.toLowerCase}GenericHttp"
    )
  }

  def outToJ(out: HttpResponse) = {
    val body = out.entity.data.asString
    val headers = out.headers.map { x => x.name -> x.value }.toMap

    JsonObject(
      "status" -> out.status.toString(),
      "body" -> body,
      "headers" -> headers
    )
  }

  def jToOut(j: Json) = {
    val body = jget(j, "body")
    val httpData = body match {
      case s: String => HttpData(s)
      case x => HttpData(Compact(x))
    }
    val httpEntity = HttpEntity(data = httpData)
    val jheaders = jgetObject(j, "headers")

    val headers: List[HttpHeader] = List(
        HttpHeaders.`Content-Length`(httpEntity.data.toByteArray.length.toLong),
        HttpHeaders.`Date`(spray.http.DateTime.now),
        HttpHeaders.`Server`(Seq(spray.http.ProductVersion("spray-can", "1.1-M8"))) // TODO: non-hardcoded?
      ) ++ jheaders.map {
        case (h,v) =>  RawHeader(h, v.toString)
      }.toList

    HttpResponse(headers = headers, entity = httpEntity)
  }
}
