 /*
package com.whitepages.framework.client

import akka.actor.{ActorRefFactory, Props}
import com.persist.JsonOps._
import com.whitepages.framework.client.RedisClient.{RedisRequest, RedisResponse}
import com.whitepages.framework.logging.ReqIdAndSpanOut
import com.whitepages.framework.util.ClassSupport
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[whitepages] trait RedisBaseClientLike {
  def call(mapper: ClientLoggingMapper[RedisRequest, RedisResponse])
          (request: RedisRequest, id: ReqIdAndSpanOut,
           percent: Int, duration: Option[Any], logExtra: Option[Any]): Future[RedisResponse]

  def stop: Future[Unit]
}

private[client] object RedisBaseClient {

  def redisResponseToJson(response: RedisResponse): Json = response match {
    case _ =>
      val jString = response.toString
      Json(jString)
  }

}

private[client] class RedisBaseClient(actorFactory: ActorRefFactory,
                                      clientName: String,
                                      healthRequest: RedisRequest,
                                      //retryInterval: FiniteDuration,
                                      callTimeout: FiniteDuration,
                                      db: Option[Int],
                                      password: Option[String],
                                      logMapper: ExtendedClientLogging.Mapper)
  extends ClassSupport with RedisBaseClientLike {
  private[this] implicit val ec: ExecutionContext  = system.dispatcher
  private[this] val clientConfig          = getClientConfig(clientName)
  private[this] val logResponse           = clientConfig.getBoolean("logResponse")
  private[this] val logRequest            = clientConfig.getBoolean("logRequest")
  //private[this] val connections           = clientConfig.getInt("connections")
  private[this] val extendedClientLogging = ExtendedClientLogging(logMapper, clientName)


  type Bytes = Array[Byte]


  private[this] val client: BaseClientTrait[RedisRequest, RedisResponse] = {
    def driverProps(driverMessages: DriverMessages[RedisRequest, RedisResponse], host: String, port: Int) =
      Props(classOf[RedisActor], driverMessages, clientName, healthRequest, callTimeout, host, port, db, password)
    Balancer[RedisRequest, RedisResponse](driverProps, actorFactory, s"${clientName}")
  }

  // TODO missing timing parameter!!
  override def call(mapper: ClientLoggingMapper[RedisRequest, RedisResponse])
                   (request: RedisRequest, allIds: ReqIdAndSpanOut, percent: Int = 100, duration: Option[Any] = None, logExtra: Option[Any]): Future[RedisResponse] = {
    def reqLogBits(shouldLog: Boolean = logRequest) = if (shouldLog) {
      JsonObject("UUIDS" -> JsonArray(allIds.requestId.trackingId, allIds.requestId.spanId, allIds.spanIdOut)) ++
        mapper.inToJ(request)
    } else emptyJsonObject

    val startTime = System.nanoTime()
    val responseFuture = client.call(request, allIds.requestId, percent)

    responseFuture onComplete {
      case Failure(ex) =>
        val endTime = System.nanoTime
        val duration = endTime - startTime
        val requestInfo = () => mapper.inToJ(request)
        val responseInfo = () => JsonObject("XERROR" -> ex.toString)
        val metricsInfo = () => emptyJsonObject
        val info = ExtendedClientLogging.LogInfo("", "", requestInfo, responseInfo, metricsInfo, logRequest, logResponse, true, logExtra)
        extendedClientLogging.logIt(duration, allIds.requestId, allIds.spanIdOut, info, None)
      case Success(resp: RedisResponse) =>
        val endTime = System.nanoTime
        val duration = endTime - startTime
        val requestInfo = () => mapper.inToJ(request)
        val responseInfo = () => mapper.outToJ(resp)
        val metricsInfo = () => emptyJsonObject
        val info = ExtendedClientLogging.LogInfo("", "", requestInfo, responseInfo, metricsInfo, logRequest, logResponse, false, logExtra)
        extendedClientLogging.logIt(duration, allIds.requestId, allIds.spanIdOut, info, None)
    }
    responseFuture
  }

  def stop: Future[Unit] = {
    client.stop
  }

}
*/
