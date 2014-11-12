package com.whitepages.framework.client

import akka.actor.{Props, ActorRefFactory}
import com.persist.JsonOps._
import java.net.URLEncoder
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure, Random}
import spray.http.HttpMethods._
import spray.http.{HttpResponse, HttpRequest}
import ExecutionContext.Implicits.global
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.logging.{noId, ReqIdAndSpanOut, AnyId}

/**
 * Client companion object contains support for mocks.
 */
object SOLRClient {

  /**
   * The trait for SOLR mocks.
   */
  trait SOLRMock {
    /**
     * The method for calling a mock.
     * @param args the SOLR request args.
     * @return  a future containing the SOLR result.
     */
    def call(args: JsonObject): Future[Json]
  }

  /**
   * Available mocks. Selected by mockName configuration parameter.
   */
  var mocks = Map[String, SOLRMock]()

}

/**
 * This is the client used to talk to SOLR search services.
 * @param actorFactory the context in which to create child actors.
 * @param clientName  the name of the client. This is used to lookup configuration info.
 * @param mapper an optional mapper for customizing messages written to client log.
 */
case class SOLRClient(private val actorFactory: ActorRefFactory,
                      private val clientName: String,
                      private val mapper: ExtendedClientLogging.Mapper = ExtendedClientLogging.defaultMapper
                       ) extends ClassSupport {
  private[this] val extendedClientLogging = ExtendedClientLogging(mapper, clientName)
  private[this] val clientConfig = getClientConfig(clientName)
  private[this] val logResponse = clientConfig.getBoolean("logResponse")
  private[this] val logRequest = clientConfig.getBoolean("logRequest")
  getFiniteDuration("cycleConnections",config = clientConfig)
  private[this] val mainUri = clientConfig.getString("uri")
  private[this] val healthUri = clientConfig.getString("healthUri")
  private[this] val mockName = clientConfig.getString("mockName")
  // TODO remove old mocks
  private[this] val oldMock = (mockName != "none" && HttpClientMockFactories.mocks.isDefinedAt(mockName))
  if (oldMock) log.error(noId, JsonObject("msg" -> "old mocks are deprecated and will be removed soon", "client" -> clientName))
  private[this] val newMock = (mockName != "none" && SOLRClient.mocks.isDefinedAt(mockName))
  private[this] val httpMockClient: BaseHttpClientLike =
    if (oldMock) {
      HttpClientMockFactories.mocks(mockName)()
    } else {
      null
    }

  private val httpClient =
    if (!oldMock && !newMock) {
      def driverProps(driverMessages: DriverMessages[HttpRequest, HttpResponse], host: String, port: Int) =
        Props(classOf[HttpActor], driverMessages, host, port, clientName, healthRequest)
      Balancer[HttpRequest, HttpResponse](driverProps, actorFactory, clientName)
    } else {
      null
    }

  private[this] val healthRequest = HttpRequest(
    method = GET,
    uri = healthUri)

  private[this] val jsonMapper = JsonLoggingMapper(clientName)

  private[this] def encode(s: String) = URLEncoder.encode(s, "UTF-8")

  /**
   * Send a request to SOLR search service.
   *
   * @param args a Json object containing the request name value pairs.
   * @param id the id for the request.
   * @param percent a percent to increase timeouts. The default is 100 which leaves timeouts unchanged.
   *                A value greater than 100 increases timeouts. A value less than 100 decreases timeouts.
   * @param timing  if not None, sends a ClientDuration message to the local monitor extension.
   * @param logExtra an optional value to be passed to a custom log mapper for client logs.
   * @return a future that when complete will contain the Json result.
   */
  def callSOLR(args: JsonObject,
               id: AnyId,
               percent: Int = 100,
               timing: Option[Any] = None,
               logExtra: Option[Any] = None): Future[Json] = {
    val METHOD = "callSOLR"
    val allIds = ReqIdAndSpanOut(id)
    val extArgs = args ++ JsonObject("trackingId" -> allIds.requestId.trackingId, "spanId" -> allIds.spanIdOut)

    val q = (extArgs.keys map {
      case name => encode(name) + "=" + encode(jgetString(extArgs, name))
    }).mkString("&")
    val uri = s"$mainUri?" + q
    val req = HttpRequest(
      method = GET,
      uri = uri
    )
    val startTime = System.nanoTime()
    val responseFuture = if (newMock) {
      val mock = SOLRClient.mocks(mockName)
      mock.call(args)
    } else if (oldMock) {
      httpMockClient.call(jsonMapper)(req, allIds, percent, timing, logExtra) map {
        case httpResponse => Json(httpResponse.entity.data.asString)
      }
    } else {
      httpClient.call(req, allIds.requestId, percent) map {
        case httpResponse => Json(httpResponse.entity.data.asString)
      }
    }
    responseFuture onComplete {
      case ccc => {
        try {
          val endTime = System.nanoTime()
          val duration = endTime - startTime
          val requestInfo = () => args
          val metricsInfo = () => emptyJsonObject
          val (ok, responseInfo) = ccc match {
            case Failure(ex) =>
              val responseInfo = () => JsonObject("XERROR" -> ex.toString)
              (true, responseInfo)
            case Success(resp: Json) =>
              val responseInfo = () => resp
              (false, responseInfo)
          }
          val info = ExtendedClientLogging.LogInfo("", METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, ok, logExtra)
          extendedClientLogging.logIt(duration, allIds.requestId, allIds.spanIdOut, info, timing)
        } catch {
          case ex:Throwable =>
            log.error(id, JsonObject("msg"->"on complete failed", "client"->clientName, "method"->METHOD))
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
