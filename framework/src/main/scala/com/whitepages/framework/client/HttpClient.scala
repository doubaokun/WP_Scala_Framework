package com.whitepages.framework.client

import akka.actor.{Props, ActorRefFactory}
import java.net.URLEncoder
import scala.concurrent.{ExecutionContext, Future}
import spray.http._
import ExecutionContext.Implicits.global
import spray.http.parser.HttpParser
import spray.http.HttpRequest
import spray.http.HttpHeaders.RawHeader
import com.whitepages.framework.util.{JsonUtil, ClassSupport}
import com.whitepages.framework.logging.{noId, ReqIdAndSpanOut, AnyId}
import com.persist.JsonOps._
import scala.util.{Success, Failure}
import spray.http.MediaTypes._

// TODO object ContentEncoding
// TODO object MethodType 'GET' || 'POST'

/**
 * This object contains the request and response case classes used by the HTTPClient
 * and support for mocks.
 */
object HttpClient extends ClassSupport {

  private[client] trait BodyFactory {
    private[client] val mediaType: MediaType

    private[client] def apply(e: HttpEntity): Body
  }

  /**
   * This is the common trait for all HTTP request and response bodies.
   * There is a different instance of this trait for each kind of body.
   */
  trait Body {
    private[client] def encode: HttpEntity

    private[client] def toJson: Json
  }


  private[client] object JsonBodyFactory extends BodyFactory {
    private[client] val mediaType = MediaTypes.`application/json`

    private[client] def apply(e: HttpEntity) = new JsonBody(Json(e.asString))

  }

  /**
   * A Json HTTP request or response body
   * @param body the Json string.
   */
  case class JsonBody(body: Json) extends Body {

    import JsonBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(JsonUtil.safeCompact(body)))

    private[client] def toJson = body
  }

  private[client] object HtmlBodyFactory extends BodyFactory {
    private[client] val mediaType = MediaTypes.`text/html`

    private[client] def apply(e: HttpEntity) = new HtmlBody(e.asString)
  }


  /**
   * A Json HTTP request or response body
   *
   * @param body the Json.
   */
  case class HtmlBody(body: String) extends Body {

    import HtmlBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(body))

    private[client] def toJson = body

  }

  private[client] object XmlBodyFactory extends BodyFactory {
    private[client] val mediaType = MediaTypes.`text/xml`

    private[client] def apply(e: HttpEntity) = new XmlBody(e.asString)
  }

  /**
   * An XML HTTP request or response body
   *
   * @param body the XML.
   */
  case class XmlBody(body: String) extends Body {

    import XmlBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(body))

    private[client] def toJson = body
  }

  private[client] object SoapBodyFactory extends BodyFactory {
    private[client] val mediaType = MediaTypes.`application/soap+xml`

    private[client] def apply(e: HttpEntity) = new SoapBody(e.asString)
  }

  /**
   * An XML/Soap HTTP request or response body
   *
   * @param body the soap XML.
   */
  case class SoapBody(body: String) extends Body {

    import SoapBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(body))

    private[client] def toJson = body
  }

  private[client] object TextBodyFactory extends BodyFactory {
    private[client] val mediaType = MediaTypes.`text/plain`

    private[client] def apply(e: HttpEntity) = new TextBody(e.asString)
  }

  /**
   * A plain text HTTP request or response body
   *
   * @param body the text.
   */
  case class TextBody(body: String) extends Body {

    import TextBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(body))

    private[client] def toJson = body
  }

  private[client] object JavascriptBodyFactory extends BodyFactory {
    private[client] val mediaType = register(MediaType.custom(
      mainType = "text",
      subType = "javascript",
      binary = false
    ))

    private[client] def apply(e: HttpEntity) = new JavascriptBody(e.asString)
  }

  /**
   * A text/javascript body. Needed to support bogus facebook api!
   * Now deprecated. Use config httpResultType="text/plain" instead.
   *
   * @param body the javascript (or for facebook Json) text.
   */
  @deprecated("", "") case class JavascriptBody(body: String) extends Body {

    import TextBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(body))

    private[client] def toJson = body

  }

  /*
  private[client] object ThriftBodyFactory extends BodyFactory {
    private[client] val mediaType = com.whitepages.framework.util.Thrift.thriftType

    private[client] def apply(e: HttpEntity) = new ThriftBody(e.data.toByteArray)

    private[client] def fromJson(j: Json, clientName: String) {
      j match {
        case s: String => {
          try {
            ThriftBody(new sun.misc.BASE64Decoder().decodeBuffer(s))
          } catch {
            case ex: Throwable => throw ClientFailException(JsonObject("client" -> clientName, "msg" -> "can not decode thrift"))
          }
        }
        case _ => throw ClientFailException(JsonObject("client" -> clientName, "msg" -> "bad thrift body"))
      }
    }
  }

  /**
   * A Thrift HTTP request or response body
   * @param body the binary thrift value.
   */
  case class ThriftBody(body: Array[Byte]) extends Body {

    import TextBodyFactory.mediaType

    private[client] def encode = HttpEntity(ContentType(mediaType), HttpData(body))

    private[client] def toJson = new sun.misc.BASE64Encoder().encode(body)
  }
  */

  private[client] object Bodies {
    private def item(fact: BodyFactory) = fact.mediaType.toString() -> fact

    var map = Map[String, BodyFactory](
      item(JsonBodyFactory),
      item(HtmlBodyFactory),
      item(XmlBodyFactory),
      item(SoapBodyFactory),
      item(TextBodyFactory),
      item(JavascriptBodyFactory)
      //item(ThriftBodyFactory)
    )

    def register(fact: BodyFactory) {
      map += item(fact)
    }
  }

  /**
   * This case class is used for requests sent via the HTTP client.
   * @param path the path part of the url.
   * @param body the optional body to be send as part of a POST. Default is None (for gets).
   * @param method  either get or post. Default is get.
   * @param queryParams  a set of name value pairs for the query part if the url (the stuff after ?).
   *                     Default is an empty map.
   * @param headers a set of name value pairs for headers to be sent.
   *                Default is an empty map.
   */
  case class Request(path: String,
                     body: Option[Body] = None,
                     //body: String = "",
                     method: String = "get",
                     queryParams: Map[String, String] = Map.empty,
                     headers: Map[String, String] = Map.empty) extends ClassSupport {

    private[this] val DEFAULT_ENCODING = "UTF-8" // TODO put this into a common library or config, also used in WebServiceClient

    private[this] val httpMethod = method match {
      case "get" => HttpMethods.GET
      case "post" => HttpMethods.POST
      case "delete" => HttpMethods.DELETE
      case "put" => HttpMethods.PUT
      case _ => throw new IllegalArgumentException("Method must be get or post")
    }

    private[this] val rawHeaders: List[HttpHeader] = headers.map {
      case (key, value) => RawHeader(key, value)
    }.toList
    private[this] val parsedHttpHeaders = HttpParser.parseHeaders(rawHeaders)
    private[this] val httpHeaders = parsedHttpHeaders._2 // TODO we probably shouldn't ignore any errors in parsing.

    private[this] val uriParams = queryParams.map {
      case (key, value) => URLEncoder.encode(key, DEFAULT_ENCODING) + "=" + URLEncoder.encode(value, DEFAULT_ENCODING)
    }.mkString("&")

    private[this] val uriString = if (uriParams.isEmpty) path else List(path, uriParams).mkString("?")
    private[this] val uri = Uri(uriString)
    private[this] val requestEntity = body match {
      case Some(b) => b.encode
      case _ => HttpEntity.Empty
    }
    private[client] val httpRequest = HttpRequest(method = httpMethod, uri = uri, headers = httpHeaders, entity = requestEntity)
  }


  /**
   * This case class is used for responses from the HTTPClient.
   * @param status The HTTP response code.
   * @param body The optional result body.
   * @param headers The name-value pairs of the response headers.
   * @param isSuccess True if this response was a successful result for the request sent.
   */
  case class Response(status: Int = 200, body: Option[Body] = None, headers: Map[String, String] = Map[String, String](), isSuccess: Boolean = true)


  private[client] def makeResponse(httpResponse: HttpResponse, id: AnyId, httpResponseType: String) = {
    val status = httpResponse.status.intValue

    def responseType(): Option[String] = {
      if (httpResponseType != "") {
        Some(httpResponseType)
      } else {
        httpResponse.headers.find(x => x.is("content-type")) match {
          case Some(h) =>
            Some(h.value)
          case None => None
        }
      }
    }

    //val body: Option[Body] = httpResponse.headers.find(x => x.is("content-type")) match {
    val body: Option[Body] = responseType() match {
      case Some(v) =>
        //        val v = h.value
        val semi = v.indexOf(';')
        val v1 = if (semi > 0) v.substring(0, semi) else v
        Bodies.map.get(v1) match {
          case Some(f) => Some(f.apply(httpResponse.entity))
          case None =>
            log.error(id, JsonObject("msg" -> "unknown content type", "content-type" -> v, "v1" -> v1))
            None
        }
      case None => None
    }

    val headers: Map[String, String] = httpResponse.headers.map {
      h => h.name -> h.value
    }.toMap

    val isSuccess = httpResponse.status.isSuccess
    Response(status, body, headers, isSuccess)
  }

  /**
   * The trait for HTTP mocks.
   */
  trait HttpMock {
    /**
     * The method for calling a mock.
     * @param request the HTTP request.
     * @return  a future containing the HTTP response.
     */
    def call(request: Request): Future[Response]
  }

  /**
   * Available mocks. Selected by mockName configuration parameter.
   */
  var mocks = Map[String, HttpMock]()

  private[client] def checkStatusCode2XX(status: StatusCode) {
    if (status.intValue < 200 || status.intValue >= 300) throw new Exception("non-2XX response status code from service")
  }

}

/**
 * This is the client used to call services that use HTTP but were not built using this framework.
 * @param actorFactory the context in which to create child actors.
 * @param clientName  the name of the client. This is used to lookup configuration info.
 * @param healthRequest an HTTP request used for health checks.
 * @param mapper an optional mapper for customizing messages written to client log.
 */
case class HttpClient(private val actorFactory: ActorRefFactory,
                      private val clientName: String,
                      private val healthRequest: HttpClient.Request,
                      private val mapper: ExtendedClientLogging.Mapper = ExtendedClientLogging.defaultMapper
                       ) extends ClassSupport {

  import HttpClient._

  private[this] val extendedClientLogging = ExtendedClientLogging(mapper, clientName)
  private[this] val clientConfig = getClientConfig(clientName)
  private[this] val logResponse = clientConfig.getBoolean("logResponse")
  private[this] val logRequest = clientConfig.getBoolean("logRequest")
  private[this] val healthReq = healthRequest.httpRequest
  private[this] val mockName = clientConfig.getString("mockName")
  private[this] val httpResponseType = clientConfig.getString("httpResponseType")
  // TODO remove old mocks
  private[this] val oldMock = (mockName != "none" && HttpClientMockFactories.mocks.isDefinedAt(mockName))
  if (oldMock) log.error(noId, JsonObject("msg" -> "old mocks are deprecated and will be removed soon", "client" -> clientName))
  private[this] val newMock = (mockName != "none" && HttpClient.mocks.isDefinedAt(mockName))

  private[this] val httpMockClient: BaseHttpClientLike =
    if (oldMock) {
      HttpClientMockFactories.mocks(mockName)()
    } else {
      null
    }
  private val httpClient =
    if (!oldMock && !newMock) {
      def driverProps(driverMessages: DriverMessages[HttpRequest, HttpResponse], host: String, port: Int) =
        Props(classOf[HttpActor], driverMessages, host, port, clientName, healthReq)
      Balancer[HttpRequest, HttpResponse](driverProps, actorFactory, clientName)
    } else {
      null
    }


  private[this] val httpMapper = HttpLoggingMapper(clientName)

  /**
   * Sends a request to the service that this client is connected to.
   * @param request the HTTP request.
   * @param id the id for the request.
   * @param percent a percent to increase timeouts. The default is 100 which leaves timeouts unchanged.
   *                A value greater than 100 increases timeouts. A value less than 100 decreases timeouts.
   * @param timing  if not None, sends a ClientDuration message to the local monitor extension. Default is None.
   * @param logExtra an optional value to be passed to a custom log mapper for client logs.
   * @return a future that when complete will contain the HTTP result.
   */
  def callHttp(request: Request,
               id: AnyId,
               percent: Int = 100,
               timing: Option[Any] = None,
               logExtra: Option[Any] = None): Future[Response] = {
    val METHOD = "callHTTP"
    val httpRequest = request.httpRequest

    val allIds = ReqIdAndSpanOut(id)
    val startTime = System.nanoTime()
    val responseFuture = if (newMock) {
      val mock = HttpClient.mocks(mockName)
      mock.call(request)
    } else if (oldMock) {
      httpMockClient.call(httpMapper)(httpRequest, allIds, percent, timing, logExtra) map {
        case httpResponse =>
          HttpClient.checkStatusCode2XX(httpResponse.status)
          makeResponse(httpResponse, id, httpResponseType)
      }
    } else {
      httpClient.call(httpRequest, allIds.requestId, percent) map {
        case httpResponse =>
          HttpClient.checkStatusCode2XX(httpResponse.status)
          makeResponse(httpResponse, id, httpResponseType)
      }
    }
    responseFuture onComplete {
      case ccc => {
        try {
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val requestInfo = () => {
            val j1 = request.body match {
              case Some(b: HttpClient.Body) => JsonObject("body" -> b.toJson)
              case None => emptyJsonObject
            }
            j1 ++ JsonObject(
              "path" -> request.path,
              "headers" -> request.headers,
              "method" -> request.method,
              "queryParams" -> request.queryParams
            )
          }
          val metricsInfo = () => emptyJsonObject
          val (ok, responseInfo) = ccc match {
            case Failure(ex) =>
              val responseInfo = () => JsonObject("XERROR" -> ex.toString)
              (true, responseInfo)
            case Success(resp: Response) =>
              val responseInfo = () => {
                val j1 = resp.body match {
                  case Some(b: HttpClient.Body) => JsonObject("body" -> b.toJson)
                  case None => emptyJsonObject
                }
                j1 ++ JsonObject(
                  "status" -> resp.status,
                  "headers" -> request.headers
                )
              }
              (false, responseInfo)
          }
          val info = ExtendedClientLogging.LogInfo("", METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, ok, logExtra)
          extendedClientLogging.logIt(duration, allIds.requestId, allIds.spanIdOut, info, timing)
          responseInfo
        } catch {
          case ex: Throwable =>
            log.error(id, JsonObject("msg" -> "on complete failed", "client" -> clientName, "method" -> METHOD))
        }
      }
    }
    responseFuture
  }

  def stop: Future[Unit] = {
    val f1 = if (httpMockClient != null) httpMockClient.stop else Future.successful(())
    val f2 = if (httpClient != null) httpClient.stop else Future.successful(())
    val f3 = f1 zip f2 map {
      case x => ()
    }
    f3
  }
}
