/*
package com.whitepages.framework.client

import akka.actor.ActorRefFactory
import com.whitepages.framework.client.RedisClient.{RedisHealthCheck, RedisRequest, RedisResponse}
import com.whitepages.framework.logging.{AnyId, ReqIdAndSpanOut}
import com.whitepages.framework.util.ClassSupport
import org.joda.time.DateTime
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
 * RedisClient messages and case classes.
 */
private[whitepages] object RedisClient {
  // TODO DRS add descs to all params below.
  /**
   * Base trait for a request to Redis.
   */
  sealed trait RedisRequest

  private[whitepages] case object RedisHealthCheck extends RedisRequest

  /**
   *
   * @param requestId
   * @param cmd
   * @param data
   */
  case class RedisGet(requestId: AnyId, cmd: RedisCmd, data: RedisDataType) extends RedisRequest

  /**
   *
   * @param requestId
   * @param cmd
   * @param data
   * @param ttl
   */
  case class RedisSet(requestId: AnyId, cmd: RedisCmd, data: RedisDataType, ttl: Option[RedisTtl] = None) extends RedisRequest {
    def getTtl: RedisTtl = ttl.getOrElse(RedisTtlNoOp(data.key))
  }

  /**
   * @see [[com.whitepages.framework.client.RedisClient.RedisResponseData]] for data response
   */
  sealed trait RedisResponse

  /**
   *
   * @param requestId
   * @param data
   */
  case class RedisResponseData(requestId: AnyId, data: RedisDataType) extends RedisResponse

  /**
   * Indicates acknoledgement of the command
   * @param requestId
   */
  case class RedisResponseAck(requestId: AnyId) extends RedisResponse

  /**
   * Indicates command succeeded
   * @param requestId
   */
  case class RedisResponseSuccess(requestId: AnyId) extends RedisResponse

  /**
   * Indicates the TTL command succeeded
   * @param requestId
   */
  case class RedisResponseTtl(requestId: AnyId) extends RedisResponse

  /**
   * Indicates that the request is not yet implemented
   * @param requestId
   */
  case class RedisResponseNotImplemented(requestId: AnyId) extends RedisResponse

  /**
   * Wraps a Redis Exception
   * @param requestId
   * @param exception
   */
  case class RedisResponseFailure(requestId: AnyId, exception: Exception) extends RedisResponse

  /**
   * Base trait for a redis ttl definition
   */
  sealed trait RedisTtl

  /**
   * Defines a TTL as a FiniteDuration
   * @param key
   * @param duration
   */
  case class RedisTtlDuration(key: String, duration: FiniteDuration) extends RedisTtl

  /**
   * Defines a TTL as a future DateTime
   * @param key
   * @param timestamp
   */
  case class RedisTtlExpireAt(key: String, timestamp: DateTime) extends RedisTtl

  /**
   * Removes the TTL from key
   * @param key
   */
  case class RedisTtlRemove(key: String) extends RedisTtl

  /**
   * Sets the TTL on a key to infinite
   * @param key
   */
  case class RedisTtlInfinite(key: String) extends RedisTtl

  /**
   * Performs no action on a TTL, use when you want to update a value without changing the TTL
   * @param key
   */
  case class RedisTtlNoOp(key: String) extends RedisTtl

  /**
   * Retrieves the TTL for key
   * @param key
   */
  case class RedisTtlGet(key: String) extends RedisTtl

  /**
   * RedisCmds determine the kind of action will sent to redis.
   */
  sealed trait RedisCmd

  /**
   * Get data from a key
   */
  case object RedisCmdGet extends RedisCmd

  /**
   * Set data into a key
   */
  case object RedisCmdSet extends RedisCmd

  /**
   * Increment a counter
   */
  case object RedisCmdIncr extends RedisCmd

  /**
   * Decrement a counter
   */
  case object RedisCmdDecr extends RedisCmd

  /**
   * Check to see if a key exists
   */
  case object RedisCmdExists extends RedisCmd

  /**
   * Check to see if a key exists in a hash
   */
  case object RedisCmdHashExists extends RedisCmd

  /**
   * Get the 'type' of a key
   */
  case object RedisCmdType extends RedisCmd

  /**
   * Delete a key
   */
  case object RedisCmdDel extends RedisCmd

  /**
   * Get the current DB size
   */
  case object RedisCmdDbSize extends RedisCmd

  /**
   * Get the current number of fields in the has stored at key
   */
  case object RedisCmdHashSize extends RedisCmd

  /**
   * Base trait for a data bearing class
   */
  sealed trait RedisDataBaseType

  /**
   * Data bearing class that operates on a single Key
   */
  sealed trait RedisDataType extends RedisDataBaseType {
    val key: String
  }

  /**
   * @param key
   * @param data
   */
  case class RedisDataHash(key: String, data: Map[String, String]) extends RedisDataType

  /**
   * @param key
   * @param data
   */
  case class RedisDataSet(key: String, data: Set[String]) extends RedisDataType

  /**
   * @param key
   * @param data
   */
  case class RedisDataList(key: String, data: Seq[String]) extends RedisDataType

  /**
   *
   * @param key
   * @param data
   */
  case class RedisDataBoolean(key: String, data: Boolean) extends RedisDataType

  /**
   *
   * @param key
   * @param data
   */
  case class RedisDataLong(key: String, data: Long) extends RedisDataType

  /**
   * Wraps a key and String value
   * @param key
   * @param value
   */
  case class RedisDataKeyValue(key: String, value: String) extends RedisDataType

  /**
   * Wraps a key
   * @param key
   */
  case class RedisDataKey(key: String) extends RedisDataType

  /**
   * Wraps an empty value
   * @param key
   */
  case class RedisDataEmpty(key: String = "") extends RedisDataType

}

/**
 * This client connects to an external redis server. Queries can be sent via the a Balancer Message interface.
 *
 * @param actorFactory
 * @param clientName the clientName, config will use the key wp.clients.clientName
 * @param callTimeout Time before redis will timeout
 * @param db The redis DB number to connect to.
 * @param password The redis password - optional
 *
 */
private[whitepages]
// TODO DRS move callTimeout, db and password to config
// TODO DRS add doc for logMapper
case class RedisClient(actorFactory: ActorRefFactory,
                  clientName: String,
                  callTimeout: FiniteDuration,
                  db: Option[Int] = None,
                  password: Option[String] = None,
                  logMapper: ExtendedClientLogging.Mapper = ExtendedClientLogging.defaultMapper)
  extends ClassSupport {
  private[this] implicit val ec: ExecutionContext = system.dispatcher
  private[this] val healthRequest = RedisHealthCheck
  private[this] val clientConfig  = getClientConfig(clientName)
  //private[this] val retryInterval = getFiniteDuration("cycleConnections", config=clientConfig)
  private[this] val redisClient: RedisBaseClient =
    new RedisBaseClient(actorFactory, clientName, healthRequest, callTimeout, db, password, logMapper)

  private[this] val redisMapper = RedisLoggingMapper(clientName)

  private[this] def callRedis = redisClient.call(redisMapper) _

  // TODO DRS document params
  /**
   *
   * @see RedisRequest are defined in [[com.whitepages.framework.client.RedisClient.RedisGet]]
   *      [[com.whitepages.framework.client.RedisClient.RedisSet]]
   *
   * @example
   * {{{
   *        val client = RedisClient(info.refFactory, clientName, 2.seconds, Some(7))
   *        val requestId = RequestId()
   *        val redisSet = RedisSet(requestId, RedisCmdSet, RedisDataKeyValue(key, "Bar"))
   *        val redisGet = RedisGet(requestId, RedisCmdGet, RedisDataKey(key))
   *        val redisDel = RedisSet(requestId, RedisCmdDel, RedisDataKey(key))
   *
   *        val f = client.call(redisSet, requestId)
   *        f map {
   *          case RedisResponseData(x, y) => assert(y.key === key)
   *          case x => assert(false, s"Fail $x")
   *        }
   *        f recover {
   *          case ex: NotAvailableException =>
   *          log.error(noId, "Send failed: " + ex.toString)
   *        }
   *        Await.result(f, 10 seconds)
   * }}}
   *
   * @param request
   * @param id
   * @param percent
   * @param duration
   * @return Future[ [[com.whitepages.framework.client.RedisClient.RedisResponse]] ]
   *
   *
   */
  def call(request: RedisRequest,
           id: AnyId, percent: Int = 100,
           duration: Option[Any] = None,
           logExtra: Option[Any] = None): Future[RedisResponse] = {
    val reqIdAndSpanOut = ReqIdAndSpanOut(id)
    callRedis(request, reqIdAndSpanOut, percent, duration, logExtra)
  }

  /**
   * Stops the client
   * @return Future[Unit]
   */
  def stop: Future[Unit] = {
    redisClient.stop
  }

}
*/
