package com.whitepages.framework.monitor

import com.codahale.metrics._
import com.persist.JsonOps._
//import scala.collection.JavaConversions._
import scala.collection.mutable
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import com.codahale.metrics.jvm._
import com.whitepages.framework.logging.{noId, AnyId}
import com.whitepages.framework.util.{ClassSupport, ActorSupport, CheckedActor}
import scala.concurrent.Promise
import scala.collection.JavaConversions._
import scala.reflect.runtime.universe._

/**
 * This objects contains messages that can be sent to the monitor.
 */
object Monitor extends ClassSupport {

  /**
   * The common trait for messages sent to the monitor actor.
   */
  trait MonitorMessage

  // for app code call the REST reset method instead
  private[framework] case object Reset extends MonitorMessage

  private[framework] case object ServerQueueSize extends MonitorMessage

  private[framework] case object GetAllMetrics extends MonitorMessage

  private[framework] case object GetAlert extends MonitorMessage

  private[framework] case class Drained(p: Promise[Unit])

  private[framework] case class ClientAvailabilityNotification(clientName: String, isUp: Boolean) extends MonitorMessage

  private[framework] case class ClientRetryNotification(clientName: String, id: AnyId) extends MonitorMessage

  private[framework] case class ClientRequest(name: String, nano: Long) extends MonitorMessage

  private[framework] case class ThriftCommand(cmd: String) extends MonitorMessage

  private[framework] case class ThriftTime(cmd: String, nano: Long, monitor: Option[Any] = None) extends MonitorMessage

  private[framework] case object ThriftError extends MonitorMessage

  private[framework] case class JsonCommand(cmd: String) extends MonitorMessage

  private[framework] case class JsonTime(cmd: String, nano: Long, monitor: Option[Any] = None) extends MonitorMessage

  private[framework] case object JsonError extends MonitorMessage

  private[framework] case object ServerError extends MonitorMessage

  private[framework] case class ClientCallStarted(name: String) extends MonitorMessage

  private[framework] case class ClientCallCompleted(name: String) extends MonitorMessage

  private[framework] case class RegisterClient(name: String, ext: (MetricRegistry) => PartialFunction[Any, Unit])

  /**
   * This message is sent to a monitor extension, when a handler response includes
   * a monitor value of the form Some[Any].
   * @param nano the number of nanoseconds the request needed.
   * @param isThrift true if the request was Thrift, false if Json.
   * @param monitor  the monitor value from the handler.
   */
  case class Duration(nano: Long, isThrift: Boolean, monitor: Any) extends MonitorMessage

  /**
   * This message is sent to a monitor extension when the client call includes a timing
   * parameter of the form Some[Any].
   * @param nano the number of nanoseconds the request needed.
   * @param clientName the name of the client.
   * @param monitor  the value from the timing parameter.
   */
  case class ClientDuration(nano: Long, clientName: String, monitor: Any) extends MonitorMessage

  private[framework] case object ToggleTestMetric extends MonitorMessage

  private[framework] case class ClientConnections(name: String, connections: Int) extends MonitorMessage

  private[framework] def getJson(metrics: MetricRegistry): Json = {
    val meters = metrics.getMeters map {
      case (name, meter) =>
        name -> JsonObject("count" -> meter.getCount,
          "mean" -> meter.getMeanRate,
          "m1" -> meter.getOneMinuteRate,
          "m5" -> meter.getFiveMinuteRate,
          "m10" -> meter.getFifteenMinuteRate
        )
    }
    val histograms = metrics.getHistograms map {
      case (name, hist) =>
        val snap = hist.getSnapshot
        name -> JsonObject("min" -> snap.getMin
          , "max" -> snap.getMax
          , "mean" -> snap.getMean
          , "p99" -> snap.get99thPercentile
          , "p95" -> snap.get95thPercentile
          , "p75" -> snap.get75thPercentile
          , "p50" -> snap.getMedian
          , "stdDev" -> snap.getStdDev
          , "count" -> hist.getCount
        )
    }
    val gauges = metrics.getGauges map {
      case (name, gauge) =>
        val g = gauge.getValue match {
          case d: Double if d.isNaN => 0.0 // JSON doesn't define a NaN so prevent that from going out
          case jc: java.util.Collection[_] =>
            /*
              metrics-jvm returns a collection for jvm.thread.deadlocks;
              convert to Scala to prevent the mapper from failing with "bad json value"
             */
            if (jc.isEmpty) Seq()
            else Seq(jc.iterator())
          case other: Any => other
        }

        name -> g
    }

    val counter = metrics.getCounters map {
      case (name, counter) =>
        name -> counter.getCount
    }

    JsonObject("meters" -> meters, "hist" -> histograms, "gauges" -> gauges, "counters" -> counter)
  }

  private def alertStatus[T](current: Comparable[T], compare: String, warn: T, error: T)(implicit tag: TypeTag[T]): String = {
    val a = current.compareTo(warn)
    "ok"
  }

  private[framework] def getAlert(metrics: MetricRegistry): Json = {
    val alerts = config.getObject("wp.alert")
    alerts map {
      case (n, v) =>
        // types long, double, string
        // n  is metric path
        val metric: Metric = metrics.getMetrics.get(n)
        metric match {
          case c: Counter =>
            val l: Long = c.getCount
          case m: Meter =>
            // complex, need qual
            val value: Long = m.getCount
          case h: Histogram =>
          // complex, need qual
          //case g: Gauge =>
          //  g.getValue match {
          //    case d: Double =>
          //    case s: String =>
          //  }
        }
    }
    //alerts.
    //  case Map.Entry(kind, conf1) =>
    // }
    //alerts.
    // to get fields or metric type
    // get fields of specific metric
    // get values and compare

    // ("warn" | "critical") => (metricName, metricValue)
    val all = Map[String, (String, Json)]()

    val split = all.groupBy {
      case (kind, (metric, value)) => kind
    }
    val critical = jgetObject(split, "critical", "critical")
    val warn = jgetObject(split, "warn", "warn")
    JsonObject("state" -> "ok", "critical" -> critical, "warn" -> warn)
  }

}

private[framework] class Monitor(ext: (MetricRegistry) => PartialFunction[Any, Unit], serviceName: String) extends CheckedActor with ActorSupport {

  import Monitor._

  // TODO set up metricSelect

  // queue is created here so that maintains state during reset
  private[this] var serverQueueSize = 0
  val clientStates = config.getObject("wp.clients") map {
    case (name, value) =>
      (name, ClientState(name))
  }

  private[this] val all = AllMetrics()
  private[this] var nonJmxReportersStarted = false
  private[this] var drained: Option[Promise[Unit]] = None

  MonitorGC.addGCMonitor(self)

  /*
  Client-scoped
   */

  case class ClientState(name: String) {
    var available: Boolean = true
    var queueSize = 0
    var connections = 0
  }

  case class ClientMetrics(metrics: MetricRegistry, name: String) {
    private[this] val state = clientStates(name)

    import state._

    private[this] val availabilityGauge = new Gauge[String] {
      def getValue() = if (available) "UP" else "DOWN"
    }
    metrics.register("client." + name + ".available", availabilityGauge)

    private[this] val queueSizeGauge: Gauge[Int] = metrics.register(s"client.$name.queue", new Gauge[Int] {
      def getValue() = queueSize
    })

    private[this] val connectionGauge = metrics.register(s"client.$name.connections", new Gauge[Int] {
      def getValue = connections
    })

    private[this] val requestMeter: Meter = metrics.meter(s"client.$name.requests")
    private[this] val retryMeter: Meter = metrics.meter(s"client.$name.retry")
    private[this] val availMeter: Meter = metrics.meter(s"client.$name.notAvail")
    private[this] val requestHistExp: Histogram = metrics.register(s"client.$name.latency-5min.microsecs",
      new Histogram(new ExponentiallyDecayingReservoir()))

    def add(v: Long) {
      requestMeter.mark()
      requestHistExp.update(v)
    }

    def signalUnavailable() {
      availMeter.mark()
      available = false
    }

    def signalAvailable() {
      available = true
    }

    def isAvailable = available

    def signalRetry() {
      retryMeter.mark()
    }

    def retryCount = retryMeter.getCount

    def incr() {
      queueSize += 1
    }

    def decr() {
      queueSize -= 1
    }

    def setConnections(c: Int) {
      connections = c
    }

  }

  /*
  Requester metrics + metrics for all clients
   */
  case class AllMetrics() {

    // Note Gauges should have a Json types (String, Boolean, Long, ...)
    val registry = new MetricRegistry()

    /**
     * This is a start of letting clients completely control their own metrics
     */
    object RegisteredMetrics {
      private[this] var registrations = Map[String, (MetricRegistry) => PartialFunction[Any, Unit]]()

      var extActs: PartialFunction[Any, Unit] = PartialFunction.empty[Any, Unit]

      def add(name: String, ext: (MetricRegistry) => PartialFunction[Any, Unit]) {
        if (!registrations.isDefinedAt(name)) {
          registrations += (name -> ext)
          extActs = extActs.orElse(ext(registry))
        }
      }

      def register() {
        extActs = PartialFunction.empty[Any, Unit]
        registrations foreach {
          case (name, ext) =>
            extActs = extActs.orElse(ext(registry))
        }
      }
    }

    // jrn replaced following line causing 2 calls to ext
    //var extActs = ext(registry) // add metrics passed from outside, return a Receive function to deal with them
    var extActs = PartialFunction.empty[Any, Unit]

    private[this] var requestMeter: Meter = _
    private[this] var errorMeter: Meter = _
    private[this] var requestHistExp: Histogram = _
    private[this] var requestHist: Histogram = _
    //private[this] var cmdHist: Map[String, Histogram] = _

    val serverQueueGauge = new Gauge[Integer] {
      def getValue() = serverQueueSize
    }

    var maxQueue = 0
    val maxg = new Gauge[Integer] {
      def getValue() = maxQueue
    }

    def requestAdd(v: Long) {
      requestHist.update(v)
      requestHistExp.update(v)
    }

    def incr {
      serverQueueSize += 1
      if (serverQueueSize > maxQueue) maxQueue = serverQueueSize
    }

    def decr {
      serverQueueSize -= 1
      if (serverQueueSize == 0) checkDrained()
    }

    var gcCount = 0
    val gcCountG = new Gauge[Integer] {
      def getValue() = gcCount
    }

    var gcTime = 0L
    val gcTimeG = new Gauge[Long] {
      def getValue() = gcTime
    }

    var ygcCount = 0
    val ygcCountG = new Gauge[Integer] {
      def getValue() = ygcCount
    }

    var ygcTime = 0L
    val ygcTimeG = new Gauge[Long] {
      def getValue() = ygcTime
    }

    private[this] var testMetricToggle = false
    private[this] val testMetricToggleG = new Gauge[Boolean] {
      def getValue() = testMetricToggle
    }

    def toggleTestMetric: Boolean = {
      testMetricToggle = !testMetricToggle
      testMetricToggle
    }

    var clientMetrics: mutable.Map[String, Monitor.this.type#ClientMetrics] = _

    private[this] val reporter = JmxReporter.forRegistry(registry).build()

    val graphite = {
      try {
        new MonitorGraphite(registry)
      } catch {
        case ex: Throwable =>
          log.error(noId, "Graphite setup failed", ex)
          null
      }
    }
    /*
    val librato = {
      try {
        new MonitorLibrato(config, registry)
      } catch {
        case ex: Throwable =>
          log.error(noId, "Librato setup failed", ex)
          null
      }
    }
    val newrelic = {
      try {
        new MonitorNewrelic(config, registry)
      } catch {
        case ex: Throwable =>
          log.error(noId, "New Relic setup failed", ex)
          null
      }
    }
    */

    //private[this] val managedReporters = Vector(graphite, librato, newrelic).filter(_ != null)
    private[this] val managedReporters = Vector(graphite).filter(_ != null)

    def checkDrained() {
      drained map {
        case p: Promise[Unit] =>
          if (serverQueueSize == 0) {
            p.trySuccess(())
            drained = None
          }
      }
    }

    // Metric methods
    def incrementRequestMeter {
      requestMeter.mark()
    }

    def incrementErrorMeter {
      errorMeter.mark()
    }


    def resetMetrics {
      // Want to reuse the same registry
      // so, first delete all metrics it contains
      // and then readd them.
      // It would be much nicer is Coda Hale had a metrics clear/reset method.
      val registeredMetrics = registry.getNames.toVector
      val notRemoved = registeredMetrics.map(name => (name, registry.remove(name)))
        .filterNot(each => each._2)
        .map(_._1)
      if (!notRemoved.isEmpty) {
        val mString = notRemoved.mkString(", ")
        log.error(noId, s"Cannot remove metric(s) $mString during reset")
      }
      registerMetrics
    }

    def registerMetrics {
      extActs = ext(registry)
      registerServerMetrics
      registerClientMetrics
      RegisteredMetrics.register()
    }

    def registerServerMetrics {
      requestMeter = registry.meter("server.requests")
      errorMeter = registry.meter("server.errors")
      requestHistExp = registry.register("server.latency-5min.microsecs", new Histogram(new ExponentiallyDecayingReservoir()))
      requestHist = registry.register("server.latency.microsecs", new Histogram(new UniformReservoir()))
      //cmdHist = cmds.map {
      //  case cmd =>
      //    (cmd, registry.register(s"server.xcmd.$cmd.microsecs", new Histogram(new UniformReservoir())))
      //}.toMap
      registry.register("server.queue", serverQueueGauge)
      registry.register("server.maxQueue", maxg)
      registry.register("server.gc.count", gcCountG)
      registry.register("server.gc.time", gcTimeG)
      registry.register("server.ygc.count", ygcCountG)
      registry.register("server.ygc.time", ygcTimeG)
      registry.register("server.testMetric", testMetricToggleG)
      // metrics from the metrics-jvm module
      registry.register("server.jvm.gc", new GarbageCollectorMetricSet())
      registry.register("server.jvm.memory", new MemoryUsageGaugeSet())
      registry.register("server.jvm.thread", new ThreadStatesGaugeSet())
      /*
      Leaving out due to JSON conversion problems:
      1. FileDescriptorRatioGauge
       */

      val osBean = ManagementFactory.getPlatformMXBean(classOf[OperatingSystemMXBean])

      registry.register("server.jvm.cpu.load", new Gauge[Double]() {
        @Override
        def getValue() = {
          // What % CPU load this current JVM is taking, from 0.0-1.0
          osBean.getProcessCpuLoad()
        }
      });

      registry.register("server.jvm.system.load", new Gauge[Double]() {
        @Override
        def getValue() = {
          // What % load the overall system is at, from 0.0-1.0
          osBean.getSystemCpuLoad()
        }
      })

      // Disabled to to Linux bug when rinning under jsvc
      /*
      osBean match {
        case b: UnixOperatingSystemMXBean =>
          registry.register("server.jvm.file-descriptors", new Gauge[Long]() {
            @Override
            def getValue() = {
              b.getOpenFileDescriptorCount()
            }
          })
      }
      */
    }

    def registerClientMetrics = {
      clientMetrics = config.getObject("wp.clients") map {
        case (name, value) =>
          (name, ClientMetrics(registry, name))
      }
    }

    def startReporting(jmx: Boolean, other: Boolean = false) {
      if (jmx) reporter.start()
      if (other) {
        managedReporters.foreach(r => scala.util.control.Exception.catching(classOf[Throwable])
          .withApply(log.error(noId, "Exception thrown while starting reporter", _)) {
          r.start()
        }
        )
      }
    }

    def closeReporters {
      managedReporters.foreach(r => scala.util.control.Exception.catching(classOf[Throwable])
        .withApply(log.error(noId, "Exception thrown while closing reporter", _)) {
        r.close()
      }
      )
      reporter.close()
    }

    registerMetrics
  }

  def clientMetrics(name: String) = all.clientMetrics.get(name)

  /*
  Non-JMX reporters lazily started to avoid skewing the stats with warm-up measurements
  (started on the first invocation of reset)
   */
  all.startReporting(jmx = true)


  def rec = {
    val f: PartialFunction[Any, Unit] = {
      case Reset =>
        scala.util.control.Exception.catching(classOf[Throwable]).withApply(// TODO: remove?
          t => {
            log.error(noId, s"$t")
            sender ! "error"
          }) {
          all.resetMetrics
          if (!nonJmxReportersStarted) {
            all.startReporting(jmx = false, other = true)
            nonJmxReportersStarted = true
          }
          sender ! "ok"
        }
      case GetAllMetrics =>
        sender ! Monitor.getJson(all.registry)
      case GetAlert =>
        sender ! Monitor.getAlert(all.registry)
      case ServerQueueSize =>
        sender ! serverQueueSize
      case Drained(p: Promise[Unit]) =>
        drained = Some(p)
        all.checkDrained()
      case JsonCommand(cmd) =>
        all.incrementRequestMeter
        all.incr
      case ThriftCommand(cmd) =>
        all.incrementRequestMeter
        all.incr
      case JsonTime(cmd, nano, mon) =>
        //all.requestAdd(nano / 1000)
        all.requestAdd(nano / 1000)
        mon match {
          case Some(x) => self ! Duration(nano, false, x)
          case None =>
        }
        //all.updateCommandMetrics(cmd, nano)
        all.decr
      case ThriftTime(cmd, nano, mon) =>
        all.requestAdd(nano / 1000)
        mon match {
          case Some(x) => self ! Duration(nano, true, x)
          case None =>
        }
        //all.updateCommandMetrics(cmd, nano)
        all.decr
      case JsonError =>
        all.incrementErrorMeter
        all.decr
      case ThriftError =>
        all.incrementErrorMeter
        all.decr
      case ServerError =>
        all.incrementErrorMeter
      case ClientCallStarted(clientName) =>
        if (all.clientMetrics.contains(clientName)) all.clientMetrics(clientName).incr()
      case ClientCallCompleted(clientName) =>
        if (all.clientMetrics.contains(clientName)) all.clientMetrics(clientName).decr()
      case ("gc", micro: Long) =>
        all.gcCount += 1
        all.gcTime += micro
      case ("ygc", micro: Long) =>
        all.ygcCount += 1
        all.ygcTime += micro
      case ClientRequest(name, nano) =>
        if (all.clientMetrics.contains(name)) all.clientMetrics(name).add(nano / 1000)
      case ClientRetryNotification(name, id) =>
        if (all.clientMetrics.contains(name)) all.clientMetrics(name).signalRetry()
        else log.warn(id, s"No client metrics for $name")
      case ClientAvailabilityNotification(name, isUp) =>
        if (all.clientMetrics.contains(name)) {
          if (isUp) all.clientMetrics(name).signalAvailable()
          else all.clientMetrics(name).signalUnavailable()
        } else
          log.warn(noId, s"No client metrics for $name")
      case ClientConnections(name, connections) =>
        if (all.clientMetrics.contains(name)) {
          all.clientMetrics(name).setConnections(connections)
        } else
          log.warn(noId, s"No client metrics for $name")
      case ToggleTestMetric =>
        sender ! all.toggleTestMetric
      case RegisterClient(name, ext) =>
        all.RegisteredMetrics.add(name, ext)
    }

    def fail: PartialFunction[Any, Unit] = {
      case x =>
        log.warn(noId, s"Bad monitor request: $x")
    }

    //f.orElse(all.extActs).orElse(all.RegisteredMetrics.extActs).orElse(fail)
    f.orElse(all.RegisteredMetrics.extActs).orElse(all.extActs).orElse(fail)
  }

  override def postStop(): Unit = {
    all.closeReporters
  }
}

