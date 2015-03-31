package com.whitepages.framework.client

import com.persist.JsonOps._
import scala.concurrent.{ExecutionContext, Future}
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import akka.actor.{Props, ActorRefFactory}
import com.whitepages.framework.util.ClassSupport
import java.net.URLEncoder
import ExecutionContext.Implicits.global
import com.whitepages.framework.logging._
import spray.http.HttpHeaders.RawHeader
import scala.util.Failure
import scala.util.Success
import com.whitepages.framework.logging.RequestId
import spray.http._

// TODO get rid of this
private[client] object HttpClientMockFactories {
  var mocks = Map[String, () => BaseHttpClientLike]()
}

/**
 * Client companion object contains support for mocks.
 */
object ClientMocks {

  /**
   * The trait for Client mocks.
   */
  trait ClientMock {
    /**
     * The method for calling a mock.
     * Note this is also used for Thrift calls; these are converted to/from Json for the mock call.
     * @param cmd the command.
     * @param method the method name.
     * @param request  the JSON request.
     * @return  a future containing the Json response.
     */
    def call(cmd: String, method: String, request: Json): Future[Json]
  }

  /**
   * Available mocks. Selected by mockName configuration parameter.
   */
  var mocks = Map[String, ClientMock]()


  private[client] def checkStatusCode2XX(status: StatusCode) {
    if (status.intValue < 200 || status.intValue >= 300) throw new Exception("non-2XX response status code from service")
  }
}

/**
 * This is the client used to call other services build using this framework.
 * It can also be used to call Json and Thrift services written using other languages or frameworks.
 * @param actorFactory the context in which to create child actors.
 * @param clientName  the name of the client. This is used to lookup configuration info.
 * @param mapper an optional mapper for customizing messages written to client log.
 */
case class JsonClient(private val actorFactory: ActorRefFactory,
                      private val clientName: String,
                      private val mapper: ExtendedClientLogging.Mapper = ExtendedClientLogging.defaultMapper)
  extends ClassSupport {

  protected val extendedClientLogging = ExtendedClientLogging(mapper, clientName)
  protected val clientConfig = getClientConfig(clientName)
  protected val logResponse = clientConfig.getBoolean("logResponse")
  protected val logRequest = clientConfig.getBoolean("logRequest")
  private[this] val healthUri = clientConfig.getString("healthUri")
  protected val mockName = clientConfig.getString("mockName")
  // TODO remove old mocks
  protected val oldMock = (mockName != "none" && HttpClientMockFactories.mocks.isDefinedAt(mockName))
  if (oldMock) log.error(noId, JsonObject("msg" -> "old mocks are deprecated and will be removed soon", "client" -> clientName))
  protected val newMock = (mockName != "none" && ClientMocks.mocks.isDefinedAt(mockName))
  private[this] val healthRequest = HttpRequest(
    method = GET,
    uri = healthUri)

  protected val httpMockClient: BaseHttpClientLike =
    if (oldMock) {
      HttpClientMockFactories.mocks(mockName)()
    } else {
      null
    }

  protected val httpClient =
    if (!oldMock && !newMock) {
      def driverProps(driverMessages: DriverMessages[HttpRequest, HttpResponse], host: String, port: Int) =
        Props(classOf[HttpActor], driverMessages, host, port, clientName, healthRequest)
      Balancer[HttpRequest, HttpResponse](driverProps, actorFactory, clientName)
    } else {
      null
    }

  private[this] val jsonMapper = JsonLoggingMapper(clientName)

  protected def makeHeaders(id: AnyId, spanId: String) = {
    id match {
      case id: RequestId => List(
        RawHeader("X-Tracking_id", id.trackingId),
        RawHeader("X-Span_id", id.spanId),
        RawHeader("X-Seq_id", spanId))
      case _ => List()
    }
  }

  /**
   * Sends a Json get request to the service that this client is connected to.
   * @param cmd the name of the command to the service.
   * @param data  the Json request. Default is an empty Json object.
   * @param id the id for the request.
   * @param percent a percent to increase timeouts. The default is 100 which leaves timeouts unchanged.
   *                A value greater than 100 increases timeouts. A value less than 100 decreases timeouts.
   * @param timing  if not None, sends a ClientDuration message to the local monitor extension.
   * @param logExtra an optional value to be passed to a custom log mapper for client logs.
   * @return a future that when complete will contain the Json result.
   */
  def getJson(cmd: String, id: AnyId, data: JsonObject = emptyJsonObject,
              percent: Int = 100, timing: Option[Any] = None,
              logExtra: Option[Any] = None): Future[Json] = {
    val METHOD = "getJson"
    val allIds = ReqIdAndSpanOut(id)
    val headers = makeHeaders(allIds.requestId, allIds.spanIdOut)
    val params = makeParams(data)
    val q = if (jsize(data) == 0) "" else "?" + params.mkString("&")

    val request = HttpRequest(
      method = GET,
      uri = "/" + cmd + q,
      headers = headers
    )

    val startTime = System.nanoTime()
    val responseFuture = if (newMock) {
      val mock = ClientMocks.mocks(mockName)
      mock.call(cmd, METHOD, data)
    } else if (oldMock) {
      httpMockClient.call(jsonMapper)(request, allIds, percent, timing, logExtra) map {
        case httpResponse =>
          ClientMocks.checkStatusCode2XX(httpResponse.status)
          Json(httpResponse.entity.data.asString)
      }
    } else {
      httpClient.call(request, allIds.requestId, percent) map {
        case httpResponse => Json(httpResponse.entity.data.asString)
      }
    }
    responseFuture onComplete {
      case ccc => {
        try {
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val requestInfo = () => data
          val metricsInfo = () => emptyJsonObject
          val (ok, responseInfo) = ccc match {
            case Failure(ex) =>
              val responseInfo = () => JsonObject("XERROR" -> ex.toString)
              (true, responseInfo)
            case Success(resp: Json) =>
              val responseInfo = () => resp
              (false, responseInfo)
          }
          val info = ExtendedClientLogging.LogInfo(cmd, METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, ok, logExtra)
          extendedClientLogging.logIt(duration, allIds.requestId, allIds.spanIdOut, info, timing)
        } catch {
          case ex: Throwable =>
            log.error(id, JsonObject("msg" -> "on complete failed", "client" -> clientName, "method" -> METHOD))
        }
      }
    }

    responseFuture
  }

  // TODO put this into a common library or config
  private val DEFAULT_ENCODING = "UTF-8"

  private def makeParams(data: JsonObject): Iterable[String] = {
    data map {
      case (name, s: String) => URLEncoder.encode(name, DEFAULT_ENCODING) + "=" + URLEncoder.encode(s, DEFAULT_ENCODING)
      case (name, j) => URLEncoder.encode(name, DEFAULT_ENCODING) + "=(" + URLEncoder.encode(Compact(j), DEFAULT_ENCODING) + ")"
    }
  }

  // TODO timing does not seem to be used!

  /**
   * Sends a Json POST request to the service that this client is connected to.
   * @param cmd the name of the command to the service.
   * @param data  the Json request
   * @param id the id for the request.
   * @param percent a percent to increase timeouts. The default is 100 which leaves timeouts unchanged.
   *                A value greater than 100 increases timeouts. A value less than 100 decreases timeouts.
   * @param timing  if not None, sends a ClientDuration message to the local monitor extension.
   * @param logExtra an optional value to be passed to a custom log mapper for client logs.
   * @return a future that when complete will contain the Json result. Default is None.
   */
  def postJson(cmd: String, data: Json, id: AnyId,
               percent: Int = 100, timing: Option[Any] = None,
               logExtra: Option[Any] = None): Future[Json] = {
    val METHOD = "postJson"
    val allIds = ReqIdAndSpanOut(id)
    val headers = makeHeaders(allIds.requestId, allIds.spanIdOut)
    val request = HttpRequest(
      method = POST,
      uri = "/" + cmd,
      headers = headers,
      entity = HttpEntity(`application/json`, Compact(data))
    )
    //val response = callJson(request, allIds, percent, timing, logExtra).map(httpResp => BaseHttpClient.httpResponseToJson(httpResp))
    //response
    val startTime = System.nanoTime()
    val responseFuture = if (newMock) {
      val mock = ClientMocks.mocks(mockName)
      mock.call(cmd, METHOD, data)
    } else if (oldMock) {
      //val mock = HttpClientMockFactories.mocks(mockName)()
      httpMockClient.call(jsonMapper)(request, allIds, percent, timing, logExtra) map {
        case httpResponse =>
          ClientMocks.checkStatusCode2XX(httpResponse.status)
          Json(httpResponse.entity.data.asString)
      }
    } else {
      httpClient.call(request, allIds.requestId, percent) map {
        case httpResponse => Json(httpResponse.entity.data.asString)
      }
    }
    responseFuture onComplete {
      case ccc => {
        try {
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val requestInfo = () => data
          val metricsInfo = () => emptyJsonObject
          val (ok, responseInfo) = ccc match {
            case Failure(ex) =>
              val responseInfo = () => JsonObject("XERROR" -> ex.toString)
              (true, responseInfo)
            case Success(resp: Json) =>
              val responseInfo = () => resp
              (false, responseInfo)
          }
          val info = ExtendedClientLogging.LogInfo(cmd, METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, ok, logExtra)
          extendedClientLogging.logIt(duration, allIds.requestId, allIds.spanIdOut, info, timing)
        } catch {
          case ex: Throwable =>
            log.error(id, JsonObject("msg" -> "on complete failed", "client" -> clientName, "method" -> METHOD))
        }
      }
    }
    responseFuture
  }

   /**
   * This method should be called after all use of the client to stop it.
   * @return a future that completes when the client is fully stopped.
   */
  def stop: Future[Unit] = {
    val f1 = if (httpMockClient != null) httpMockClient.stop else Future.successful(())
    val f2 = if (httpClient != null) httpClient.stop else Future.successful(())
    val f3 = f1 zip f2 map {
      case x => ()
    }
    f3
  }
}
