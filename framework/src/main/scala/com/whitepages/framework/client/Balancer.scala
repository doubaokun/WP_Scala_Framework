package com.whitepages.framework.client

import com.whitepages.framework.service.ServiceState

import scala.concurrent.{Await, ExecutionContext, Promise, Future}
import akka.actor._
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.language.postfixOps
import com.whitepages.framework.monitor.Monitor
import Monitor._
import akka.actor.SupervisorStrategy._
import akka.actor.Terminated
import com.whitepages.framework.exceptions.{NotAvailableException, ClientTimeoutException, ClientFailException}
import com.whitepages.framework.logging.{noId, AnyId}
import com.whitepages.framework.util.{ActorSupport, CheckedActor, ClassSupport}
import com.persist.JsonOps._
import scala.concurrent.duration._
import scala.language.postfixOps
import java.util.concurrent.TimeUnit

/*
   Balancer Goals

   Manage individual connections using actors rather than a connection pool with futures

   * 1. Send to next free connection (round robin), q if none
   * 2. Reset each connection every N minutes (spread out on connections)
   * 3. Detect per connection failure
   * 4. Timeout on actual time (not time in q)
   * 5. Use first result if retries
   * 6. HTTP - Deal with spray client actor messages and remove awaits
   * 7. Support ready to send next as opposed to have response (spray withAck)
   * 8. Let driver control order of response when multiple in flight
   * 9. Compute availability and schedule health checks when not available
   * 10. Provide a clean (but sufficiently general api) for easy to write drivers
   * 11. Adjust number of connections based on load
 */

/*
   // TODO future work
   1. Support smart client (dynamic list of server ips)
   2. HTTP - Support back pressure
*/

private[client] class BalancerMessages[In, Out] {

  // ****************************************************************************************
  // to balancer from clients
  // ****************************************************************************************

  // TODO return time (minus queue) or just send to metrics???
  case class BalancerSend(in: In, id: AnyId, p: Promise[Out], percent: Int)

  case class BalancerClose(p: Promise[Unit])

}

private[client] object driverIds {

  trait AllId

  case class ReqTryId(id: Long, retry: Int) extends AllId

  case class HealthId(id: Long) extends AllId

}

private[client] class DriverMessages[In, Out] {

  import driverIds._

  // ****************************************************************************************
  // to driver to itself
  // ****************************************************************************************

  case object DriverInit

  // ****************************************************************************************
  // to driver from balancer
  // ****************************************************************************************

  case class DriverSend(in: In, id: AnyId, uid: ReqTryId)

  case object DriverClose

  case class DriverHealthCheck(uid: HealthId)

  // ****************************************************************************************
  // from driver to balancer
  // ****************************************************************************************

  case object DriverReady

  case class DriverConnectFailed(msg: String)

  case object DriverAck

  case class DriverReceive(out: Out, uid: ReqTryId)

  case class DriverReceiveFail(reason: String, uid: ReqTryId, extra:Option[JsonObject] = None)

  case class DriverReceiveRetry(reason: String, uid: ReqTryId)

  case object DriverClosed

  case class DriverFail(reason: String)

  case class DriverHealthResult(ok: Boolean, uid: HealthId)

}

// ****************************************************************************************
// Balancer actor
// ****************************************************************************************

private[client] case class BalancerActor[In, Out](
                                                   driverProps: (DriverMessages[In, Out], String, Int) => Props,
                                                   clientName: String,
                                                   messages: BalancerMessages[In, Out]
                                                   )
  extends CheckedActor with ActorSupport {

  override val supervisorStrategy = stoppingStrategy

  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private[this] var reqId: Long = 0L

  import messages._

  private[this] val driverMessages = new DriverMessages[In, Out]

  import driverIds._
  import driverMessages._

  // ****************************************************************************************
  // Config
  // ****************************************************************************************

  private[this] val clientConfig = getClientConfig(clientName)
  private[this] val retries = clientConfig.getInt("retries")
  private[this] val host = clientConfig.getString("host")
  private[this] val port0 = clientConfig.getInt("port")
  private[this] val port = if (port0 != 0) port0 else config.getInt("wp.service.port")
  private[this] val availabilityThreshold = clientConfig.getInt("availabilityThreshold")
  private[this] val debug = clientConfig.getBoolean("debug")
  private[this] val maxConnections0 = clientConfig.getInt("connections")
  private[this] val cycleConnections = getFiniteDuration("cycleConnections", config = clientConfig)

  private[this] val unresponsive = getFiniteDuration("unresponsive", config = clientConfig)

  private[this] val minConnections = clientConfig.getInt("minConnections")
  private[this] val maxConnections = if (maxConnections0 < minConnections) {
    minConnections
  } else {
    maxConnections0
  }

  private def series(vals: Array[FiniteDuration])(i: Int): FiniteDuration = {
    val size = vals.size
    val v = if (i >= vals.size) vals(size - 1) else vals(i)
    v
  }

  private def namedSeries(name: String) = {
    val vals1 = clientConfig.getDurationList(name, TimeUnit.MILLISECONDS).toList map {
      case i => FiniteDuration(i.intValue(), TimeUnit.MILLISECONDS)
    }
    val vals = vals1.toArray
    series(vals) _
  }

  private[this] val callBackoff = namedSeries("callBackoff")
  private[this] val availBackoff = namedSeries("availBackoff")
  private[this] val callTimeout = namedSeries("callTimeOut")
  private[this] val availTimeout = namedSeries("availTimeout")

  // ****************************************************************************************
  // Internal types
  // ****************************************************************************************

  case class Req(UID: Long, in: In, id: AnyId, out: Promise[Out], percent: Int)

  case class ReqTry(req: Req, retry: Int)

  // ****************************************************************************************
  // Internal Messages - from balancer to balancer
  // ****************************************************************************************

  // original try or retry timeout
  case class TryTimeout(reqTryId: ReqTryId, ref: ActorRef)

  // retry only after a delay
  case class DelayedRetry(reqTry: ReqTry)

  // expire connections over time
  case object ExpireConnection

  // add a connection after a delay
  case object DelayedAdd

  // health check after delay
  case object HealthCheckDelay

  // timeout for health check
  case class HealthCheckTimeOut(uid: Long, ref: ActorRef)

  // timeout for open connection
  case class OpenUnresponsive(ref: ActorRef)

  // timeout for send to connection
  case class SendUnresponsive(ref: ActorRef)

  // timeout for close connection
  //case class CloseUnresponsive(ref: ActorRef)

  // ****************************************************************************************
  // Availability
  // ****************************************************************************************

  object availability {

    private[this] var currentAvailability: Boolean = true

    def available = currentAvailability

    private[this] var failCount: Int = 0
    private[this] var hasConnect: Boolean = true
    private[this] var healthCheckCount: Int = 0
    private[this] var healthUID: Long = 0L
    private[this] var healthCheckTimer: Option[Cancellable] = None
    private[this] var healthCheckDelayTimer: Option[Cancellable] = None
    private[this] var shuttingDown = false

    private def check() {
      val newAvailability = hasConnect && (failCount < availabilityThreshold)
      if (newAvailability != currentAvailability) {
        currentAvailability = newAvailability
        if (newAvailability) {
          monitor ! ClientAvailabilityNotification(clientName, isUp = true)
          log.info(noId, JsonObject("client" -> clientName, "avail" -> "became available"))
          failCount = 0
          hasConnect = true
          healthCheckCount = 0
          healthCheckTimer match {
            case Some(t) =>
              t.cancel()
              healthCheckTimer = None
            case None =>
          }
          healthCheckDelayTimer match {
            case Some(t) =>
              t.cancel()
              healthCheckDelayTimer = None
            case None =>
          }
        } else {
          monitor ! ClientAvailabilityNotification(clientName, isUp = false)
          log.error(noId, JsonObject("client" -> clientName, "avail" -> "not available", "hasConnect" -> hasConnect,
            "failCount" -> failCount, "newAvailability" -> newAvailability))
          actives.flush()
          queues.flush()
          healthCheck()
        }
      }
    }

    def healthCheck() {
      if (shuttingDown || currentAvailability) return
      healthUID += 1
      connections.freeConnection() match {
        case Some(ref) =>
          ref ! DriverHealthCheck(HealthId(healthUID))
          log.info(noId, JsonObject("msg" -> "sending health check", "client" -> clientName, "uid" -> healthUID.toString,
            "path" -> ref.path.toString))
          healthCheckTimer = Some(system.scheduler.scheduleOnce(availTimeout(healthCheckCount)) {
            self ! HealthCheckTimeOut(healthUID, ref)
          })
        case None =>
          //log.info(noId, JsonObject("msg" -> "health check no connections", "pending" -> connections.getPending,
          //  "connections" -> connections.currentNumberConnections, "client" -> clientName))
          healthCheckResult(false, healthUID)
      }
    }

    def healthCheckResult(ok: Boolean, uid: Long) {
      if (shuttingDown) {
        healthCheckTimer match {
          case Some(t) =>
            t.cancel()
            healthCheckTimer = None
          case None =>
        }
      } else if (!currentAvailability && uid == healthUID) {
        healthCheckTimer match {
          case Some(t) =>
            t.cancel()
            healthCheckTimer = None
          case None =>
        }
        if (ok) {
          hasConnect = true
          failCount = 0
          check()
        } else {
          healthCheckDelayTimer = Some(system.scheduler.scheduleOnce(availBackoff(healthCheckCount)) {
            self ! HealthCheckDelay
          })
          healthCheckCount += 1
        }
      }
    }

    def healthCheckDelay() {
      healthCheckDelayTimer = None
      healthCheck()
    }

    def healthCheckTimeOut(uid: Long) {
      healthCheckTimer = None
      healthCheckResult(false, uid)
    }

    def fail() {
      log.info(noId, JsonObject("msg" -> "AVAIL FAIL", "count" -> failCount,
        "client" -> clientName,
        "host" -> host,
        "port" -> port,
        "pending" -> connections.getPending, "has" -> connections.hasConnections,
        "hasConnect" -> hasConnect))
      failCount += 1
      check()
    }

    def success() {
      if (failCount != 0) log.info(noId, JsonObject("msg" -> "AVAIL SUCCESS", "count" -> failCount,
        "client" -> clientName,
        "host" -> host,
        "port" -> port,
        "pending" -> connections.getPending, "has" -> connections.hasConnections,
        "hasConnect" -> hasConnect))
      failCount = 0
      check()
    }

    def connectFail(ref: ActorRef, reason: String) {
      if (!connections.hasConnections) {
        hasConnect = false
        check()
      }
      /*
      log.info(noId, JsonObject("msg" -> "CONNECTION FAILED", "size" -> connections.currentNumberConnections,
        "client" -> clientName,
        "host" -> host,
        "port" -> port,
        "pending" -> connections.getPending, "has" -> connections.hasConnections,
        "path" -> ref.path.toString, "reason" -> reason, "hasConnect" -> hasConnect))
        */
    }

    def connectSuccess() {
      hasConnect = true
      check()
    }

    def shutdown() {
      shuttingDown = true
      healthCheckTimer match {
        case Some(t) => t.cancel()
        case None =>
      }
      actives.flush()
      queues.flush()
    }
  }

  // ****************************************************************************************
  // All current connections
  // ****************************************************************************************

  object connections {

    trait ConnectionState

    // initial state
    case object ConnInit extends ConnectionState

    // waiting for connection to open
    case object ConnPending extends ConnectionState

    // active but not ready for next request
    case object ConnBusy extends ConnectionState

    // active and ready for next request
    case object ConnFree extends ConnectionState

    // expired (draining)
    case object ConnExpired extends ConnectionState

    // waiting for connection to close
    case object ConnClosing extends ConnectionState

    // final state
    case object ConnClosed extends ConnectionState

    private[this] var pending = 0
    private[this] var expired = 0
    private[this] var closing = 0
    private[this] var free = 0
    private[this] var busy = 0

    def getPending = pending

    private[this] var shuttingDownPromise: Option[Promise[Unit]] = None
    private[this] var shuttingDown0: Boolean = false

    def shuttingDown = shuttingDown0

    class CInfo(var next: CInfo, var prev: CInfo,
                val inFlight: scala.collection.mutable.HashSet[ReqTryId],
                val ref: ActorRef,
                val t0: Long,
                var t1: Long,
                var timer: Option[Cancellable]) {

      private[this] var state0: ConnectionState = ConnInit

      def state = state0

      def setState(state1: ConnectionState) {
        state0 match {
          case ConnInit =>
          case ConnPending => pending -= 1
          case ConnBusy => busy -= 1
          case ConnFree => free -= 1
          case ConnExpired => expired -= 1
          case ConnClosing => closing -= 1
        }
        // TODO assert valid state transitions???
        state1 match {
          case ConnPending => pending += 1
          case ConnBusy => busy += 1
          case ConnFree => free += 1
          case ConnExpired => expired += 1
          case ConnClosing => closing += 1
          case ConnClosed =>
        }
        state0 = state1
      }

      setState(ConnPending)
    }

    private[this] var connectionId = 0
    private[this] val connections = mutable.HashMap[ActorRef, CInfo]()
    private[this] var nextConnection: CInfo = null
    private[this] var firstConnection: CInfo = null

    private[this] var expireCancel: Option[Cancellable] = None
    private[this] var addTimer: Option[Cancellable] = None

    def currentNumberConnections = connections.size


    def hasConnections: Boolean = {
      (connections.size - closing - expired - pending) > 0
    }

    def has(ref: ActorRef): Boolean = {
      connections.get(ref) != None
    }

    def add() {
      addTimer match {
        case Some(t) => t.cancel()
        case None =>
      }
      addTimer = None
      if (shuttingDown) return
      val t0 = System.nanoTime()
      connectionId += 1
      val ref = context.actorOf(driverProps(driverMessages, host, port), name = "connection" + connectionId)
      val timer = Some(system.scheduler.scheduleOnce(unresponsive) {
        self ! OpenUnresponsive(ref)
      })
      val ci = new CInfo(null, null, mutable.HashSet[ReqTryId](), ref, t0, 0L, timer)
      context.watch(ref)
      if (firstConnection == null) {
        ci.next = ci
        ci.prev = ci
        nextConnection = ci
        firstConnection = ci
      } else {
        var first = firstConnection
        var last = firstConnection.prev
        first.prev = ci
        last.next = ci
        ci.prev = last
        ci.next = first
      }
      connections += (ref -> ci)
      //if (!availability.available) log.info(noId, JsonObject("clientName" -> clientName, "msg" -> "adding connection", "path" -> ref.path.toString))
      monitor ! ClientConnections(clientName, connections.size)
    }

    private def scheduleAdd(delay: FiniteDuration) {
      if (shuttingDown) return
      addTimer match {
        case Some(t) =>
        case None =>
          val need = if (minConnections > 0) {
            minConnections
          } else if (queues.hasWaiting) {
            1
          } else {
            0
          }
          if (connections.size - closing - expired < need) {
            addTimer = Some(system.scheduler.scheduleOnce(delay) {
              self ! DelayedAdd
            })
          }
      }
    }

    private def findFreeConnection(): Option[ActorRef] = {
      if (free > 0) {
        val first = nextConnection
        var conn = nextConnection
        do {
          if (conn.state == ConnFree) {
            nextConnection = conn.next
            return Some(conn.ref)
          }
          conn = conn.next
        } while (conn != first)
      }
      None
    }

    def freeConnection(): Option[ActorRef] = {
      val result = findFreeConnection()
      if (connections.size < maxConnections) {
        val cnt0 = minConnections - connections.size
        val cnt = if (cnt0 > 0) {
          cnt0
        } else if (pending == 0 && result == None) {
          1
        } else {
          0
        }
        for (i <- 1 to cnt) add()
      }
      result
    }

    def send(reqTry: ReqTry, in: In, id: AnyId, ref: ActorRef) {
      val ci = connections(ref)
      assert(ci.state == ConnFree)
      val reqTryId = ReqTryId(reqTry.req.UID, reqTry.retry)
      ci.inFlight.add(reqTryId)
      ci.setState(ConnBusy)
      ref ! DriverSend(in, id, reqTryId)
    }

    def startUnresponsive(ref: ActorRef) {
      val ci = connections(ref)
      if (ci.timer == None) {
        ci.timer = Some(system.scheduler.scheduleOnce(unresponsive) {
          self ! SendUnresponsive(ref)
        })
      }
    }

    def healthReceive(ref: ActorRef) {
      val ci = connections(ref)
      ci.timer map { case t => t.cancel()}
      ci.timer = None
    }

    def receive(ref: ActorRef, uid: ReqTryId): ReqTryId = {
      val ci = connections(ref)
      ci.timer map { case t => t.cancel()}
      ci.timer = None
      if (ci.inFlight.remove(uid)) {
        if (ci.state == ConnExpired && ci.inFlight.size == 0) {
          startClose(ref)
        }
      }
      uid
    }

    def ready(ref: ActorRef) {
      if (has(ref)) {
        val ci = connections(ref)
        ci.timer map { case t => t.cancel()}
        ci.timer = None
        val t1 = System.nanoTime()
        ci.t1 = t1
        val delta = (ci.t1 - ci.t0) / 1000000
        if (!availability.available) log.info(noId, JsonObject("client" -> clientName, "millis" -> delta, "msg" -> "driver connect success"))
        if (ci.state == ConnExpired && ci.inFlight.size == 0) {
          startClose(ref)
        } else {
          ci.setState(ConnFree)
        }
      }
    }

    def fail(ref: ActorRef, msg: String) {
      if (has(ref)) {
        val ci = connections(ref)
        ci.timer map { case t => t.cancel()}
        ci.timer = None
        closed(ref, msg, 10 seconds)
      }
    }

    def setFree(ref: ActorRef) {
      val ci = connections(ref)
      if (ci.state != ConnExpired) {
        ci.setState(ConnFree)
      }
    }

    def startClose(ref: ActorRef) {
      val ci = connections(ref)
      ci.setState(ConnClosing)
      ref ! DriverClose
      scheduleAdd(1 second)
    }

    def trys(ref: ActorRef): mutable.HashSet[ReqTryId] = {
      val ci = connections(ref)
      ci.inFlight
    }

    private def checkShutDown {
      if (shuttingDown && connections.size == 0) {
        shuttingDownPromise match {
          case Some(p) =>
            if (!p.isCompleted) {
              p.success(())
              self ! PoisonPill
            }
          case None =>
        }
      }
    }

    def closed(ref: ActorRef, reason: String, delay: FiniteDuration = (1 second)) {
      val ci = connections(ref)
      ci.setState(ConnClosed)
      if (ci.prev == ci) {
        firstConnection = null
        nextConnection = null
      } else {
        if (firstConnection == ci) firstConnection = ci.next
        if (nextConnection == ci) nextConnection = ci.next
        val prev = ci.prev
        val next = ci.next
        prev.next = next
        next.prev = prev
      }
      //if (!availability.available) log.info(noId, JsonObject("clientName" -> clientName, "msg" -> "removing connection", "path" -> ref.path.toString,
      //  "reason" -> reason))
      connections -= ref
      if (! shuttingDown) monitor ! ClientConnections(clientName, connections.size)
      checkShutDown
      scheduleAdd(delay)
    }

    private def scheduleExpire {
      val (ok, delta) = if (shuttingDown) {
        (true, 500 milliseconds)
      } else if (cycleConnections == (0 seconds)) {
        (false, 0 seconds)
      } else {
        val size = if (connections.size == 0) 1 else connections.size
        val delta = cycleConnections / size
        (true, delta)
      }
      if (ok) {
        expireCancel = Some(system.scheduler.scheduleOnce(delta) {
          self ! ExpireConnection
        })
      }
    }

    def expire() {
      if (firstConnection != null) {
        if (firstConnection.state != ConnExpired) {
          if (firstConnection.inFlight.size == 0) {
            if (firstConnection.state != ConnPending) {
              if (!shuttingDown && !availability.available) log.info(noId, JsonObject("clientName" -> clientName, "msg" -> "expiring connection", "path" -> firstConnection.ref.path.toString))
              startClose(firstConnection.ref)
              firstConnection.setState(ConnExpired)
            }
          }
        }
      }
      if (expired > connections.size - minConnections) add()
      scheduleExpire
    }

    def shutdown(p: Promise[Unit]) {
      addTimer match {
        case Some(c) => c.cancel()
        case None =>
      }
      shuttingDownPromise = Some(p)
      shuttingDown0 = true
      expire()
      checkShutDown
      p.future map {
        case x =>
          expireCancel match {
            case Some(c) => c.cancel()
            case None =>
          }
      }
    }

    for (i <- 0 until minConnections) {
      add()
    }
    scheduleExpire
  }

  // ****************************************************************************************
  // All active requests
  // ****************************************************************************************

  object actives {

    case class Retry(retry: Int, ref: ActorRef, t0: Long)

    case class Active(req: Req, var retries: Int, var timer: Option[Cancellable], var pending: mutable.ListBuffer[Retry])

    private[this] val actives = mutable.HashMap[Long, Active]()

    def add(reqTry: ReqTry, ref: ActorRef) {
      val req = reqTry.req
      val delta = callTimeout(reqTry.retry) * req.percent / 100
      val timer: Cancellable = system.scheduler.scheduleOnce(delta) {
        self ! TryTimeout(ReqTryId(req.UID, reqTry.retry), ref)
      }
      val t0 = System.nanoTime()
      val retry = Retry(reqTry.retry, ref, t0)
      val active = actives.get(req.UID) match {
        case Some(active) =>
          active
        case None =>
          val active = Active(req, 0, Some(timer), mutable.ListBuffer[Retry]())
          actives += (req.UID -> active)
          active
      }
      active.pending.append(retry)
    }

    def remove(id: Long): Option[Promise[Out]] = {
      actives.get(id) match {
        case Some(active) =>
          active.timer match {
            case Some(c) =>
              c.cancel()
              active.timer = None
            case None =>
          }
          actives.remove(id)
          Some(active.req.out)
        case None => None
      }
    }

    def retry(reqTryId: ReqTryId, reason: String) {
      actives.get(reqTryId.id) match {
        case Some(active) =>
          if (active.retries == retries) {
            availability.fail()
            val msg = JsonObject("client" -> clientName, "reason" -> reason, "msg" -> "all retries failed")
            active.req.out.tryFailure(ClientFailException(msg))
            remove(reqTryId.id)
          } else {
            active.pending = active.pending filter {
              case retry =>
                retry.retry != reqTryId.retry
            }
            active.timer match {
              case Some(c) =>
                c.cancel()
                active.timer = None
              case None =>
            }
            if (active.pending.contains(reqTryId.retry)) {
              active.pending.remove(reqTryId.id)
            }
            if (reqTryId.retry == active.retries) {
              active.retries += 1
              val timer: Cancellable = system.scheduler.scheduleOnce(callBackoff(active.retries)) {
                self ! DelayedRetry(ReqTry(active.req, active.retries))
              }
            }
          }
        case None =>
      }
    }

    def timeout(reqTryId: ReqTryId): Option[ReqTry] = {
      actives.get(reqTryId.id) match {
        case Some(active) =>
          if (active.retries == retries) {
            availability.fail()
            val msg = JsonObject("client" -> clientName, "msg" -> "all retries failed", "retries" -> retries, "reason" -> "timeout")
            active.req.out.tryFailure(ClientTimeoutException(msg))
            remove(reqTryId.id)
            None
          } else {
            active.retries += 1
            Some(ReqTry(active.req, active.retries))
          }
        case None => None
      }
    }

    def flush() {
      for ((uid, active) <- actives) {
        val msg = JsonObject("client" -> clientName, "msg" -> "not available")
        active.req.out.tryFailure(NotAvailableException(msg))
      }
      actives.clear()
    }
  }

  // ****************************************************************************************
  // All requests waiting for an available connection
  // ****************************************************************************************

  object queues {

    // All waiting retries
    private[this] val retryQueue = mutable.Queue[ReqTry]()

    // All waiting requests
    private[this] val rqs = mutable.Queue[Req]()

    def hasWaiting = rqs.size > 0 || retryQueue.size > 0

    private def send(reqTry: ReqTry, ref: ActorRef) {
      val req = reqTry.req
      actives.add(reqTry, ref)
      connections.send(reqTry, req.in, req.id, ref)
      if (debug) log.info(noId, JsonObject("client" -> clientName, "BALANCER-MSG" -> req,
        "sender" -> sender.path.toString))
    }

    def sendOrQueue(req: Req) {
      connections.freeConnection() match {
        case Some(ref) => send(ReqTry(req, 0), ref)
        case None => rqs.enqueue(req)
      }
    }

    def retrySendOrQueue(reqRetry: ReqTry) {
      monitor ! ClientRetryNotification(clientName, reqRetry.req.id)
      connections.freeConnection() match {
        case Some(ref) => send(reqRetry, ref)
        case None => retryQueue.enqueue(reqRetry)
      }
    }

    def sendNext() {
      if (availability.available) {
        if (retryQueue.size > 0) {
          connections.freeConnection() match {
            case Some(ref) => send(retryQueue.dequeue(), ref)
            case None =>
          }
        }
        else if (rqs.size > 0) {
          connections.freeConnection() match {
            case Some(ref) =>
              val req = rqs.dequeue()
              send(ReqTry(req, 0), ref)
            case None =>
          }
        }
      }
    }

    def flush() {
      for (req <- rqs) {
        val msg = JsonObject("client" -> clientName, "msg" -> "not available")
        req.out.tryFailure(NotAvailableException(msg))
      }
      rqs.clear()
      retryQueue.clear()
    }
  }

  // ****************************************************************************************
  // Balancer actor message receiver
  // ****************************************************************************************

  def fail(ref: ActorRef, reason: String) {
    if (connections.has(ref)) {
      val reqTryIds = connections.trys(ref)
      for (reqTryId <- reqTryIds) {
        actives.retry(reqTryId, reason)
      }
      connections.closed(ref, reason)
      if (!connections.shuttingDown && !availability.available) log.error(noId, JsonObject("client" -> clientName, "driver" -> ref.path.toString,
        "msg" -> "driver failed", "reason" -> reason))
    }
  }

  def rec = {
    case x: Any =>
      if (debug) {
        log.info(noId, JsonObject("client" -> clientName, "BALANCER-MSG" -> x.toString,
          "sender" -> sender.path.toString))
      }
      try {
        rec1(x)
      } catch {
        case ex: Throwable => log.error(noId, JsonObject("client" -> clientName, "msg" -> "balancer fail", "MSG" -> x.toString), ex)
      }
  }

  def rec1: PartialFunction[Any, Unit] = {
    // ****************************************************************************************
    // Messages from client
    // ****************************************************************************************

    case BalancerSend(in, id, p, percent) =>
      if (!availability.available || connections.shuttingDown) {
        val msg = JsonObject("client" -> clientName, "msg" -> "not available")
        p.tryFailure(NotAvailableException(msg))
      } else {
        reqId += 1
        val req = Req(reqId, in, id, p, percent)
        queues.sendOrQueue(req)
      }

    case BalancerClose(p) =>
      if (debug) log.info(noId, JsonObject("msg" -> "shutting down client", "client" -> clientName))
      availability.shutdown()
      connections.shutdown(p)

    // ****************************************************************************************
    // Messages from driver
    // ****************************************************************************************

    case DriverReady =>
      connections.ready(sender)
      availability.connectSuccess()
      queues.sendNext()

    case DriverConnectFailed(msg) =>
      availability.connectFail(sender, "driver connect failed:" + msg)
      connections.fail(sender, "driver connect failed: " + msg)

    case DriverAck =>
      if (connections.has(sender)) {
        connections.setFree(sender)
        queues.sendNext()
      }

    case DriverReceive(out, uid) =>
      if (connections.has(sender)) {
        val reqTryId = connections.receive(sender, uid)
        actives.remove(reqTryId.id) match {
          case Some(p) =>
            availability.success()
            p.trySuccess(out)
          case None => // already processed
        }
      }

    case DriverReceiveFail(reason, uid, extra) =>
      if (connections.has(sender)) {
        val reqTryId = connections.receive(sender, uid)
        actives.remove(reqTryId.id) match {
          case Some(p) =>
            availability.success()
            val extraProps = extra match {
              case Some(j) => j
              case None => emptyJsonObject
            }
            val msg = JsonObject("client" -> clientName, "msg" -> "failure", "reason" -> reason) ++ extraProps

            p.tryFailure(ClientFailException(msg))
          case None => // already processed
        }
      }

    case DriverReceiveRetry(reason, uid) =>
      if (connections.has(sender)) {
        val reqTryId = connections.receive(sender, uid)
        actives.retry(reqTryId, reason)
      }

    case DriverClosed =>
      if (connections.has(sender)) connections.closed(sender, "driver closed")

    case DriverFail(reason) =>
      fail(sender, reason)

    case Terminated(child) =>
      fail(sender, "death watch")

    case DriverHealthResult(ok, uid) =>
      log.info(noId, JsonObject("msg" -> "health check result", "client" -> clientName, "result" -> ok, "uid" -> uid.toString))
      availability.healthCheckResult(ok, uid.id)
      if (connections.has(sender)) {
        connections.healthReceive(sender)
      }

    // ****************************************************************************************
    // Messages from balancer to itself (timer based)
    // ****************************************************************************************

    case TryTimeout(reqTryId, ref) =>
      actives.timeout(reqTryId) match {
        case Some(reqTry) =>
          queues.retrySendOrQueue(reqTry)
        case None => // already processed
      }
      if (connections.has(ref)) {
        connections.startUnresponsive(ref)
      }

    case DelayedAdd =>
      connections.add()

    case DelayedRetry(reqTry) =>
      queues.retrySendOrQueue(reqTry)

    case ExpireConnection =>
      connections.expire

    case HealthCheckDelay =>
      availability.healthCheckDelay()

    case HealthCheckTimeOut(uid, ref) =>
      log.info(noId, JsonObject("msg" -> "health check timeout", "client" -> clientName, "uid" -> uid.toString))
      availability.healthCheckTimeOut(uid)
      if (connections.has(ref)) {
        connections.startUnresponsive(ref)
      }

    case OpenUnresponsive(ref) =>
      ref ! DriverClose
      availability.connectFail(ref, "open unresponsive")
      connections.fail(ref, "open unresponsive")

    case SendUnresponsive(ref) =>
      ref ! DriverClose
      availability.connectFail(ref, "send unresponsive")
      connections.fail(ref, "send unresponsive")

    case x: Any =>
      log.error(noId, JsonObject("client" -> clientName, "UNRECOGNIZED-BALANCER-MSG" -> x.toString))

  }
}

// ****************************************************************************************
// Client API
// ****************************************************************************************


private[client] case class Balancer[In, Out](
                                              driverProps: (DriverMessages[In, Out], String, Int) => Props,
                                              actorFactory: ActorRefFactory,
                                              clientName: String
                                              )
  extends BaseClientTrait[In, Out] with ClassSupport {
  private[this] val clientConfig = getClientConfig(clientName)

  private[this] val dispatcherKey = clientConfig.getString("dispatcher")

  private[this] val messages = new BalancerMessages[In, Out]

  import messages._

  private[this] implicit val ec: ExecutionContext = actorFactory.dispatcher

  private[this] def driverPropsWithDispatcher = (msgs: DriverMessages[In, Out], host: String, port: Int) => {
    driverProps(msgs, host, port).withDispatcher(dispatcherKey)
  }

  private[this] val nameSuffix = "Balancer"
  private[this] val balancerProps =
        Props(
          classOf[BalancerActor[In, Out]],
          driverPropsWithDispatcher,
          clientName,
          messages
        )

  private[this] val balancerActor = actorFactory.actorOf(balancerProps, name = clientName + nameSuffix)

  def call(in: In, id: AnyId, percent: Int): Future[Out] = {
    val percent1 = percent * ServiceState.getPercent / 100
    monitor ! ClientCallStarted(clientName)
    val p = Promise[Out]()
    balancerActor ! BalancerSend(in, id, p, percent1)
    p.future onComplete {
      case x =>
        try {
          monitor ! ClientCallCompleted(clientName)
        } catch {
          case ex:Throwable =>
        }
    }
    p.future
  }

  def stop: Future[Unit] = {
    val p = Promise[Unit]
    balancerActor ! BalancerClose(p)
    p.future
  }

}


