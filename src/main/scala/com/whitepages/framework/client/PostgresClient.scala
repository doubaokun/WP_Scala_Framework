package com.whitepages.framework.client

import akka.actor.{Props, ActorRefFactory}
import scala.concurrent.{Future, ExecutionContext}
import java.sql.{PreparedStatement, ResultSet}
import com.persist.JsonOps._
import scala.util.{Success, Failure, Random}
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.logging.{ReqIdAndSpanOut, AnyId, noId}

/**
 * This is the client used to talk to Postgres databases.
 * @param actorFactory the context in which to create child actors.
 * @param clientName  the name of the client. This is used to lookup configuration info.
 * @param mapper an optional mapper for customizing messages written to client log.
 */
case class PostgresClient(private val actorFactory: ActorRefFactory,
                          private val clientName: String,
                          private val mapper: ExtendedClientLogging.Mapper = ExtendedClientLogging.defaultMapper) extends ClassSupport {
  private[this] val clientConfig = getClientConfig(clientName)
  private[this] val logRequest = clientConfig.getBoolean("logRequest")
  private[this] val logResponse = clientConfig.getBoolean("logResponse")
  private[this] implicit val ec: ExecutionContext = system.dispatcher
  private[this] val extendedClientLogging = ExtendedClientLogging(mapper, clientName)
  private[this] val user = clientConfig.getString("user")
  private[this] val password = clientConfig.getString("password")
  private[this] val dbName = clientConfig.getString("dbName")

  private type Bytes = Array[Byte]

  private[this] val client: BaseClientTrait[PostgresRequest, PostgresResult] = {

    def driverProps(driverMessages: DriverMessages[PostgresRequest, PostgresResult], host: String, port: Int) =
      Props(classOf[PostgresActor], driverMessages, host, port,
        user, password, dbName, clientName)

    Balancer[PostgresRequest, PostgresResult](driverProps, actorFactory, clientName)
  }

  /**
   * Sends a SQL command to a Postgres database.
   *
   * @param sql the SQL command to execute
   * @param id the id for the request.
   * @param after this a function that maps th SQL result set to Json.
   * @param percent a percent to increase timeouts. The default is 100 which leaves timeouts unchanged.
   *                A value greater than 100 increases timeouts. A value less than 100 decreases timeouts.
   * @param timing  if not None, sends a ClientDuration message to the local monitor extension.
   * @param logExtra an optional value to be passed to a custom log mapper for client logs.
   * @return a future that when complete will contain the Json result from after.
   */
  def callStatement(sql: String,
                    id: AnyId,
                    after: (ResultSet) => Json,
                    percent: Int = 100,
                    timing: Option[Any] = None,
                    logExtra: Option[Any] = None): Future[Json] = {
    val METHOD = "callStatement"
    val allIds = ReqIdAndSpanOut(id)
    val id1 = allIds.requestId
    val startTime = System.nanoTime()
    val spanId = allIds.spanIdOut
    val comment = s"-- trackingId=${
      id1.trackingId
    } spaniD=${
      id1.spanId
    }}\n"
    val f = client.call(PostgresRequest(s"$comment$sql", None, after), id, percent)
    val endTime = System.nanoTime
    val duration = endTime - startTime
    f onComplete {
      case ccc =>
        try {
          ccc match {
            case Failure(ex) =>
              val requestInfo = () => JsonObject("sql" -> sql)
              val responseInfo = () => JsonObject("XERROR" -> ex.toString)
              val metricsInfo = () => emptyJsonObject
              val info = ExtendedClientLogging.LogInfo("", METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, true, logExtra)
              extendedClientLogging.logIt(duration, id1, spanId, info, timing)
            case Success(resp: PostgresResult) =>
              val requestInfo = () => JsonObject("sql" -> sql)
              val responseInfo = () => resp.after
              val metricsInfo = () => emptyJsonObject
              val info = ExtendedClientLogging.LogInfo("", METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, false, logExtra)
              extendedClientLogging.logIt(duration, id1, spanId, info, timing)
          }
        } catch {
          case ex: Throwable =>
            log.error(id, JsonObject("msg" -> "on complete failed", "client" -> clientName, "method" -> METHOD))
        }
    }

    f map {
      case result => result.after
    }
  }

  // TODO send values for ?'s/
  /**
   * Sends a SQL command to a Postgres database.
   *
   * @param sql the SQL command to execute
   * @param before
   * @param id the id for the request.
   * @param after this a function that maps th SQL result set to Json.
   * @param percent a percent to increase timeouts. The default is 100 which leaves timeouts unchanged.
   *                A value greater than 100 increases timeouts. A value less than 100 decreases timeouts.
   * @param timing  if not None, sends a ClientDuration message to the local monitor extension.
   * @param logExtra an optional value to be passed to a custom log mapper for client logs.
   * @return a future that when complete will contain the Json result from after.
   */
  private[client] def callPrepared(sql: String,
                                   before: (PreparedStatement) => Json,
                                   id: AnyId, after: (ResultSet) => Json,
                                   percent: Int = 100,
                                   timing: Option[Any] = None,
                                   logExtra: Option[Any]): Future[Json] = {
    val METHOD = "callPrepared"
    val allIds = ReqIdAndSpanOut(id)
    val id1 = allIds.requestId
    val startTime = System.nanoTime()
    val spanId = allIds.spanIdOut
    val comment = s"-- trackingId=${
      id1.trackingId
    } spaniD=${
      id1.spanId
    }}\n"
    val f = client.call(PostgresRequest(comment + sql, Some(before), after), id, percent)
    val endTime = System.nanoTime
    val duration = endTime - startTime
    f onComplete {
      case ccc =>
        try {
          ccc match {
            case Failure(ex) =>
              val requestInfo = () => JsonObject("sql" -> sql)
              val responseInfo = () => JsonObject("XERROR" -> ex.toString)
              val metricsInfo = () => emptyJsonObject
              val info = ExtendedClientLogging.LogInfo("", METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, true, logExtra)
              extendedClientLogging.logIt(duration, id1, spanId, info, timing)
            case Success(resp: PostgresResult) =>
              val requestInfo = () => JsonObject("sql" -> sql, "before" -> resp.before)
              val responseInfo = () => resp.after
              val metricsInfo = () => emptyJsonObject
              val info = ExtendedClientLogging.LogInfo("", METHOD, requestInfo, responseInfo, metricsInfo, logRequest, logResponse, false, logExtra)
              extendedClientLogging.logIt(duration, id1, spanId, info, timing)
          }
        } catch {
          case ex: Throwable =>
            log.error(id, JsonObject("msg" -> "on complete failed", "client" -> clientName, "method" -> METHOD))
        }
    }

    f map {
      case result => result.after
    }
  }

  /**
   * This method should be called after all use of the client to stop it.
   * @return a future that completes when the client is fully stopped.
   */
  def stop: Future[Unit] = {
    client.stop
  }

}
