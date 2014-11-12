package com.whitepages.framework.client

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.Tcp
import akka.util.ByteString
import com.persist.JsonOps._
import com.whitepages.framework.client.RedisClient._
import com.whitepages.framework.logging.{AnyId, noId}
import com.whitepages.framework.util.CheckedActor
import redis.{RedisClient => RedisClientConnection}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

private[client] class RedisTimeoutException(val request: RedisRequest, message: Json, val when: Long) extends Throwable

private[whitepages] object RedisActor {

  def props(driverMessages: DriverMessages[RedisRequest, RedisResponse],
            clientName: String,
            healthRequest: RedisRequest,
            callTimeout: FiniteDuration,
            host: String, port: Int, db: Option[Int] = None,
            password: Option[String] = None
             ) =
    Props(classOf[RedisActor], driverMessages, clientName, healthRequest, callTimeout, host, port, db, password)
}


/**
 *
 * @param driverMessages
 * @param host remote host
 * @param port remote port
 * @param clientName
 * @param healthRequest a valid healthRequest message
 */
// if https://github.com/etaty/rediscala doesn't workout look at https://github.com/chrisdinn/brando
private[whitepages] class RedisActor(driverMessages: DriverMessages[RedisRequest, RedisResponse],
                                     clientName: String,
                                     healthRequest: RedisRequest,
                                     callTimeout: FiniteDuration,
                                     host: String, port: Int, db: Option[Int] = None,
                                     password: Option[String] = None
                                      ) extends CheckedActor {

  import com.whitepages.framework.client.driverIds._
  import driverMessages._


  private[this] implicit val system                     = context.system
  private[this]          val balancer: ActorRef         = context.parent
  private[this] implicit val ec      : ExecutionContext = system.dispatcher

  private[this] val clientConfig = getClientConfig(clientName)

  private[this] val ddPath = "debugDriver"
  private[this] val debug  =
    if (clientConfig.hasPath(ddPath)) {
      clientConfig.getBoolean(ddPath)
    } else {
      false
    }

  private[this] val roPath = "readOnlyHost"
  private[this] val roHost =
    if (clientConfig.hasPath(roPath)) {
      clientConfig.getString(roPath)
    } else {
      host
    }


  if (debug) log.info(noId, JsonObject(
                                        "clientName" -> clientName,
                                        "msg" -> "DRIVER STARTING",
                                        "path" -> self.toString,
                                        "host" -> host,
                                        "port" -> port,
                                        "db" -> db.toString,
                                        "password" -> password.toString
                                      ))


  val redis         = RedisClientConnection(host = host, port = port, db = db, name = "RedisClientConnection")
  val redisReadOnly =
    if (host == roHost) redis
    else RedisClientConnection(host = roHost, port = port, db = db, name = "RedisClientConnection")

  try {
    // Issue a ping query just to be sure we are connected.
    val futurePong = redis.ping
    futurePong onComplete {
      case Success(r) =>
//        if (debug) log.info(noId, s"CONNECTED WITH: $r")
        val futureRoPong = redisReadOnly.ping
        futureRoPong onComplete {
          case Success(r) =>
//            if (debug) log.info(noId, s"CONNECTED WITH: $r")
            balancer ! DriverReady
          case Failure(t) => connectFailed(s"Failed to connect to read-only redis $t")
        }
        Await.result(futureRoPong, 1.seconds)
      case Failure(t) => connectFailed(s"Failed to connect to redis $t")
    }
    Await.result(futurePong, 1.seconds)
  }
  catch {
    case t: Throwable => connectFailed(s"Caught exception on connect to redis: $t")
  }

  def connectFailed(msg: String) {
    redis.stop
    if (redis != redisReadOnly) redisReadOnly.stop
    balancer ! DriverConnectFailed(msg)
    log.warn(noId, JsonObject(
                               "clientName" -> clientName,
                               "msg" -> msg,
                               "path" -> self.toString,
                               "host" -> host,
                               "port" -> port,
                               "db" -> db.toString,
                               "password" -> password.toString
                             ))
    self ! PoisonPill
  }

  def rec: Actor.Receive = {
    case x =>
      if (debug) log.info(noId, JsonObject("msg" -> "redis rec MSG", "path" -> s"$x"))
      rec1(x)
  }

  def rec1: PartialFunction[Any, Unit] = {

    case Tcp.CommandFailed(cmd) =>
      val msg = cmd.failureMessage.toString()
      log.error(noId, s"CommandFailed: $msg")
      connectFailed(msg)

    case x: RedisRequest => log.info(noId, JsonObject("msg" -> "NO-OP redis Request", "path" -> s"$x"))

    case DriverSend(in, id, uid) =>
      sendToRedis(in, id, uid)
      balancer ! DriverAck

    case DriverHealthCheck(uid) =>
      val hc: RedisRequest = RedisGet(noId, RedisCmdDbSize, RedisDataEmpty())
      sendToRedis(hc, noId, uid)
      balancer ! DriverAck

    case DriverClose =>
      redis.stop()
      if (redis != redisReadOnly) redisReadOnly.stop()
      self ! PoisonPill

    case x: Any =>
      log.error(noId, JsonObject("msg" -> "Unexpected Redis actor message: ", "path" -> s"$x"))

  }

  //TODO check for retry.
  def shouldRetry(response: RedisResponse): Boolean = {
    true
  }

  def sendToRedis(req: RedisRequest, id: AnyId, uid: AllId) {
    req match {
      case x: RedisGet =>
        val futureResponse = getRedis(x, id)
        processResponse(x.data.key, x, futureResponse, id, uid)
      case x: RedisSet =>
        val futureResponse = setRedis(x, id)
        processResponse(x.data.key, x, futureResponse, id, uid)
      case x: Any =>
        val msg = DriverReceiveFail(s"Cmd Not Implemented $x", uid.asInstanceOf[ReqTryId])
        balancer ! msg
    }
  }

  def processResponse(key: String, req: RedisRequest, futureResponse: Future[Any], id: AnyId, uid0: AllId) {
    def wrapSuccess(data: RedisDataType, uid: ReqTryId): DriverReceive = {
      DriverReceive(RedisResponseData(id, data), uid)
    }

    // TODO add timeouts back in
    //    futureResponse onComplete {
    val f = timeboxExecutionOf(futureResponse, id, req)
    f onComplete {
      case Success(r) =>
        val msg = uid0 match {
          case uid: ReqTryId =>
            r match {
              case bs: ByteString => wrapSuccess(RedisDataKeyValue(key, bs.utf8String), uid)
              case Some(bs: ByteString) => wrapSuccess(RedisDataKeyValue(key, bs.utf8String), uid)
              case x: Boolean => wrapSuccess(RedisDataBoolean(key, x), uid)
              case x: Long => wrapSuccess(RedisDataLong(key, x), uid)
              case x: RedisTimeoutException =>
                log.info(noId, JsonObject("msg" -> "Redis Timeout, sending DriverReceiveRetry", "uid" -> uid))
                DriverReceiveRetry(s"RedisTimedout", uid)
              case x: Map[String, ByteString] @unchecked =>
                wrapSuccess(RedisDataHash(key, x.mapValues { _.utf8String}), uid)
              case x: Seq[Option[ByteString]] @unchecked =>
                wrapSuccess(RedisDataList(key, x map { _.map(_.utf8String).getOrElse("") }), uid)
              case None => wrapSuccess(RedisDataKey(key), uid)
              case x =>
                log.warn(noId, JsonObject("msg" -> s"Response Not Implemented $x"))
                DriverReceiveFail(s"Response Not Implemented $x", uid)
            }
          case hid: HealthId => {
            r match {
              case x: Long => DriverHealthResult(ok = x > 0, uid = hid)
              case x: RedisTimeoutException =>
                DriverHealthResult(ok = false, uid = hid)
              case _ => DriverHealthResult(ok = false, uid = hid)
            }
          }
        }
        balancer ! msg
        if (debug) log.info(noId, JsonObject("msg" -> s"sending key $key -> $msg"))
      case Failure(t) =>
        uid0 match {
          case uid: ReqTryId =>
            t match {
              case x: RedisTimeoutException =>
                balancer ! DriverReceiveRetry(s"Future Failed $x", uid)
                if (debug) log.info(noId, JsonObject("msg" -> s"Sending Retry", "request" -> s"${x.request}"))
              case _ =>
                balancer ! DriverReceiveFail(s"Future Failed $t", uid)
                if (debug) log.info(noId, JsonObject("msg" -> s"Unknown Exception $t"))
            }
          case hid: HealthId => {
            balancer ! DriverHealthResult(ok = false, uid = hid)
          }
        }
    }
  }

  def getRedis(request: RedisGet, id: AnyId): Future[Any] = {
    val cmd = request.cmd
    val key = request.data.key
    val now = System.nanoTime()
    cmd match {
      case RedisCmdExists => redisReadOnly.exists(key)
      case RedisCmdGet => request.data match {
        case x: RedisDataKey => redisReadOnly.get(key)
        case x: RedisDataHash => redisReadOnly.hgetall(key)
        case x: RedisDataList => redisReadOnly.hmget(key, x.data: _*)
      }
      case RedisCmdExists => request.data match {
        case x: RedisDataKeyValue => redisReadOnly.hexists(key, x.value)
      }
      case RedisCmdDbSize => redisReadOnly.dbsize()
      case RedisCmdHashSize => redisReadOnly.hlen(key)
      case _ => Future.failed(new RedisTimeoutException(request, JsonObject("msg" -> s"Get Method Not Yet Implemented"), when = now))
    }
  }

  def setRedis(request: RedisSet, id: AnyId): Future[Any] = {
    val cmd: RedisCmd = request.cmd
    val data = request.data
    val ttl = request.getTtl
    val now = System.nanoTime()
    data match {
      case (d: RedisDataKey) if cmd == RedisCmdIncr => redis.incr(d.key)
      case (d: RedisDataKey) if cmd == RedisCmdDecr => redis.decr(d.key)
      case (d: RedisDataKey) if cmd == RedisCmdDel => redis.del(d.key)
      case (d: RedisDataList) if cmd == RedisCmdDel => redis.hdel(d.key, d.data: _*)
      //      case (d: RedisDataKeys) if cmd == RedisCmdDel => redis.del(d.keys:_*)
      case (d: RedisDataKeyValue) if cmd == RedisCmdSet => ttl match {
        case t: RedisTtlDuration => redis.setex(d.key, t.duration.toSeconds, d.value)
        case _ => redis.set(d.key, d.value)
      }
      case (d: RedisDataHash) if cmd == RedisCmdSet => redis.hmset(d.key, d.data)
      case _ => Future.failed(new RedisTimeoutException(request, JsonObject("msg" -> s"Get Method Not Yet Implemented"), when = now))
    }
  }

  def setTtl(ttl: RedisTtl) {
    ttl match {
      case x: RedisTtlInfinite => redis.persist(x.key)
      case x: RedisTtlRemove => redis.persist(x.key)
      case x: RedisTtlDuration => redis.expire(x.key, x.duration.toSeconds)
      case x: RedisTtlGet => redis.ttl(x.key)
      case x: RedisTtlExpireAt =>
        val expireInSeconds = x.timestamp.getMillis * 1000
        redis.expireat(x.key, expireInSeconds)
      case x: RedisTtlNoOp => // NoOp
    }
  }

  //  From ASC Activity.scala:65
  private[this] def timeboxExecutionOf[T](f: Future[T], id: AnyId, request: RedisRequest): Future[T] = {
    val p = Promise[T]()
    val now = System.nanoTime()
    val cancellation: Cancellable = context.system.scheduler.scheduleOnce(callTimeout) {
      p.failure({
        if (debug) log.info(id, s"Firing Timeout guard for plan activity $this")
        new RedisTimeoutException(request, JsonObject("msg" -> s"Activity timed out after $callTimeout"), when = now)
      })
    }
    val callOrTimeoutF = Future.firstCompletedOf(Seq(f, p.future))

    callOrTimeoutF.onComplete {
      case Success(_) =>
        cancellation.cancel()
        if (debug) log.debug(id, s"Timeout guard for redis request $this cancelled")
      case Failure(redisTimeout: RedisTimeoutException) =>
        val processingLag = FiniteDuration(System.nanoTime() - redisTimeout.when, TimeUnit.NANOSECONDS)
        if (debug) log.debug(id, s"Timeout guard for redis request $this timed out after $callTimeout, processed with time skew ${(processingLag - callTimeout).toMillis} ms")
      case Failure(t: Throwable) =>
        cancellation.cancel()
        if (debug) log.debug(id, s"Timeout guard for redis request $this cancelled: exception thrown during activity execution")
    }
    callOrTimeoutF // TODO: cancel the activity on time out
  }
}

//TODO handle multiple non-string types



