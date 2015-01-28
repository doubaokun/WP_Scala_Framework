package com.whitepages.framework.client

import com.persist.JsonOps._
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.Future
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.logging.RequestId
import com.whitepages.framework.monitor.Monitor.{ClientRequest, ClientDuration}

/**
 * This object contains code used to extend client logging.
 * The contents of messages in the client log can be
 * fully customized.
 */
object ExtendedClientLogging {

  /**
   * This data data structure contains information that is passed to a extended client logging mapper.
   * @param cmd the request command. If none, an empty string.
   * @param method the request method. If none, an empty string.
   * @param request  the Json for the request.
   * @param response the Json for the response.
   * @param metrics  various metrics from the balancer (NYI).
   * @param logRequest  true if the request should be logged.
   * @param logResponse true if the response should be logged.
   * @param sawError  true if the request failed.
   * @param extra  the value passed via the client logExtra parameter.
   */
  case class LogInfo(
                      cmd: String,
                      method: String,
                      request: () => Json,
                      response: () => Json,
                      metrics: () => JsonObject,
                      logRequest: Boolean,
                      logResponse: Boolean,
                      sawError: Boolean,
                      extra: Option[Any]
                      )

  /**
   * A mapper can optionally be provided to a client to
   * customized its client log messages.
   */
  trait Mapper {
    /**
     * Log messages are customized by converting logging info to Json.
     * @param info the info available for logging.
     * @return a future containing the Json to be included in the log.
     */
    def map(info: LogInfo): Future[JsonObject]
  }

  /**
   * This mapper turns on response logging if there was an error.
   * @param parent the underlying mapper.
   */
  case class ResponseErrorMapper(parent: Mapper) extends Mapper {
    def map(info: LogInfo): Future[JsonObject] = {
      if (info.sawError && !info.logResponse) {
        val info1 = LogInfo(info.cmd, info.method, info.request, info.response, info.metrics, info.logRequest,
          true, info.sawError, info.extra)
        parent.map(info1)
      } else {
        parent.map(info)
      }
    }

  }

  /**
   * This is the default mapper and implements the default way that logs are written.
   * It ignores info.extra and info.metrics.
   */
  object defaultMapper extends Mapper {
    def map(info: LogInfo): Future[JsonObject] = Future.successful(
      (if (info.cmd != "") JsonObject("cmd" -> info.cmd) else emptyJsonObject) ++
        (if (info.method != "") JsonObject("method" -> info.method) else emptyJsonObject) ++
        (if (info.logRequest) JsonObject("request" -> info.request()) else emptyJsonObject) ++
        (if (info.logResponse) JsonObject("response" -> info.response()) else emptyJsonObject) ++
        emptyJsonObject
    )
  }
}

private[client] case class ExtendedClientLogging(mapper: ExtendedClientLogging.Mapper, clientName: String) extends ClassSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

  val logFraction = getClientConfig(clientName).getInt("logFraction")
  require(logFraction <= 100 && logFraction >= 0, s"logFraction for $clientName must be [0..100], got $logFraction")
  val loggingEnabled = logFraction > 0
  val fractionalLoggingDisabled = logFraction == 100

  def shouldLog = loggingEnabled && (fractionalLoggingDisabled || ThreadLocalRandom.current().nextInt(100) <= logFraction - 1)   // nextInt(100) yields numbers 0-99

  // t milliseconds, duration nanoseconds
  private[client] def logIt(duration: Long,
                            id: RequestId,
                            spanId: String,
                            info: ExtendedClientLogging.LogInfo,
                            timing: Option[Any],
                            t: Long = System.currentTimeMillis()) {

    monitor ! ClientRequest(clientName, duration)
    timing.foreach(monitor ! ClientDuration(duration, clientName, _))

    if (info.sawError || shouldLog) {
      val traceId = JsonArray(id.trackingId, id.spanId, spanId)
      val props1 = JsonObject("duration" -> (duration / 1000), "@traceId" -> traceId, "client" -> clientName)

      mapper.map(info) map {
        props =>
          log.alternative("client", props ++ props1, t)
      }
    }
  }
}
