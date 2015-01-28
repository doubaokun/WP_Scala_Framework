/*
package com.whitepages.framework.service

//import com.twitter.scrooge.Info
import org.apache.commons.daemon.{DaemonContext, Daemon}
import akka.actor.{ActorRefFactory, Props, ActorSystem}
import com.typesafe.config.Config
import com.codahale.metrics.MetricRegistry
import com.persist.JsonOps._
import scala.concurrent.{Promise, Await}
import scala.concurrent.duration._
import akka.pattern._
import scala.language.postfixOps
import com.whitepages.framework.logging.{noId, LoggingControl}
import java.net.{NetworkInterface, InetAddress}
import com.whitepages.framework.monitor.{MonitorDefaults, Monitor}
import com.whitepages.framework.logging.Logger
import com.whitepages.framework.server.Server
import com.whitepages.framework.service.LifecycleMessages.{LcDraining, LcDrained, LcInfo}
import com.whitepages.framework.monitor.Monitor.{Reset, Drained}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._

/**
 * This class is the base class for all services built using
 * the framework. Currently we support both Thrift (with Json)
 * services and pure Json Services. Others may be added as needed.
 * This class should not be referenced directly; instead one of
 * it children should be used.
 *
 * Services are started in production via the jsvc daemon.
 * Services are started in development using the startServer method.
 */
private[framework] abstract class OldBaseService extends Daemon {

  // TODO remove awaits and use futures


  private[this] var system: ActorSystem = null
  private[this] var server: Server = null
  private[this] var config: Config = null
  private[this] var started = false

  //private[service] def getInfo: Map[String, Info]

  /**
   * The name of the service will be supplied by the service class.
   */
  val serviceName: String

  /**
   * Optional extension to support service specific Code Hale monitoring metrics
   * can be supplied in the service class.
   */
  val monitorExt: Option[(MetricRegistry) => PartialFunction[Any, Unit]]

  /**
   * An optional handler that supports special semantics for HTTP get requests can
   * be supplied in the service class.
   */
  val queryStringHandler: Option[(JsonObject, String) => JsonObject]

  /**
   * The interface for handling HTTP commands will be supplied in the service class.
   */
  val handlerFactory: BaseHandlerFactory

  private[this] val className = getClass.getName
  //private[this] lazy val oldLog = LoggerFactory.getLogger(className)
  //private[this] lazy val oldLogger = OldLogger(oldLog)
  protected lazy val log = Logger(className = Some(className))
  //, oldLogger = oldLogger)
  private[this] var handler: BaseHandler = null

  private def lookupBuildInfo(name: String): JsonObject = {
    try {
      val sn1 = name.replace("-", "_")
      val fn1 = s"com.whitepages.info.$sn1.BuildInfo"
      val clazz1 = Class.forName(fn1)
      val fn = fn1 + "$"
      val clazz = Class.forName(fn)
      val mod = clazz.getDeclaredField("MODULE$").get(null)
      def getVal(n: String) = {
        clazz1.getMethod(n).invoke(mod)
      }
      getVal("toMap").asInstanceOf[JsonObject]
    } catch {
      case ex: Throwable => emptyJsonObject
    }
  }

  private def getIp:String = {
    try {
      val net = NetworkInterface.getNetworkInterfaces().toSeq
      val net1 = net filter {
        case n =>
          n.getName.startsWith("en")
      }
      val all1 = net1 flatMap {
        case n => n.getInetAddresses.toSeq
      }
      val all2 = all1 filter {
        case n => n.isSiteLocalAddress
      }
      val all3 = all2 map {
        case item =>
          val parts = item.toString().split("/")
          parts(1)
      }
      val ip = all3.head
      ip
    }  catch {
      case ex:Throwable => "localhost"
    }
  }

  private def getHostIp:(String,String) = {
    try {
      val lh = InetAddress.getLocalHost()
      (lh.getHostName, lh.getHostAddress)
    } catch {
      case ex:Throwable =>
        val ip = getIp
        (ip,ip)
    }
  }

  private def getBuildInfo: JsonObject = {
    try {
      val map = lookupBuildInfo(serviceName)
      val frameworkMap = lookupBuildInfo(("scala-webservice"))
      val frameworkVersion = jgetString(frameworkMap, "version")

      val (host,ip) = getHostIp
      /*
      val ip = InetAddress.getLocalHost.getHostAddress
      val host = try {
        val canonicalHostname = InetAddress.getLocalHost.getCanonicalHostName
        val localHostName = InetAddress.getLocalHost.getHostName
        if (canonicalHostname.matches(".*[a-zA-Z].*") && canonicalHostname.split("[.]").length > 1) {
          canonicalHostname
        } else if (localHostName.split("[.]").length > 1) {
          localHostName
        } else {
          // use IP address
          ip
        }
      } catch {
        case ex: Throwable => "localhost"
      }
      */
      map ++ JsonObject("host" -> host, "ip" -> ip, "frameworkVersion" -> frameworkVersion)
    } catch {
      case ex: Throwable => emptyJsonObject
    }
  }

  private[this] var dockerInfo = emptyJsonObject
  private[this] lazy val buildInfo = getBuildInfo ++ dockerInfo

  private def getFiniteDuration(path: String, config: Config): FiniteDuration = FiniteDuration(config.getDuration(path, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  private def startSystem(debugConfig: Option[Config], isDev: Boolean, isTest: Boolean = false,
                          isDocker: Boolean = false, select: Seq[String] = Seq[String]()) {

    val monitorExt1: ((MetricRegistry) => PartialFunction[Any, Unit]) = monitorExt.getOrElse(MonitorDefaults.ext _)
    val host = jgetString(buildInfo, "host")
    val ip = jgetString(buildInfo, "ip")
    config = ServiceConfig.makeConfig(host, serviceName, debugConfig, isDocker = isDocker, select = select)


    system = ActorSystem(serviceName, config)
    val useOld = config.getBoolean("wp.logging.useOld")
    val forceUdp = config.getBoolean("wp.logging.forceUdp")
    val useUdp = forceUdp || (config.getBoolean("wp.logging.useUdp") && !isDev)
    val forceLocal = config.getBoolean("wp.logging.forceLocal") && !isDev
    val logStdout = config.getBoolean("wp.logging.logStdout")
    val clogPath = config.getString("wp.logging.logPath")
    val jlogPath = System.getProperty("LOG_DIR")
    val logPath = if (jlogPath != null) jlogPath else clogPath
    val level = LoggingControl.levelStringToInt(config.getString("wp.logging.logLevel"))
    val version = jgetString(buildInfo, "version")

    LoggingControl.serviceName0 = serviceName
    LoggingControl.service = this
    LoggingControl.system0 = system
    LoggingControl.start(version = version, host = host, ip = ip,
      isDev: Boolean, useOld = useOld, useUDP = useUdp, forceLocal = forceLocal, logStdout = logStdout,
      logPath = logPath)
    val ilevel = level match {
      case Some(i) => i
      case None => LoggingControl.WARN
    }
    LoggingControl.setLevel(ilevel)
    if (!isTest) log.info(noId, JsonObject("msg" -> "starting service", "buildInfo" -> buildInfo))

    val monitor =
      system.actorOf(Props(classOf[Monitor], monitorExt1, serviceName).withDispatcher("wp-monitor-dispatcher")
        , name = "monitor")
    LoggingControl.monitor0 = monitor
  }


  private def doApplication(refFactory: ActorRefFactory) {
    try {
      val app = handler.startApplication(refFactory)
      Await.result(app, 1 minute)
    } catch {
      case ex: Throwable =>
        log.error(noId, "Application failed", ex)
    }
  }

  private def startService(isDev: Boolean, lcInfo: Option[LcInfo] = None) {

    import LifecycleMessages._

    var ok = true
    /*
    lcInfo map {
      case info =>
        info.lcActor ! LcRunning
        try {
          Await.result(info.init, 1 minute)
        } catch {
          case ex: Throwable =>
            log.error(noId, "No init received from agent")
        }
    }
    */

    try {
      handler = handlerFactory.start(system)
    } catch {
      case ex: Throwable =>
        log.error(noId, "HandlerFactory start failed", ex)
        ok = false

    }
    val useOld = config.getBoolean("wp.logging.useOld")
    val listen = config.getString("wp.service.listen")
    val runServer = config.getBoolean("wp.service.runServer")
    val runWarmup = config.getBoolean("wp.service.runWarmup")
    val runApplication = config.getBoolean("wp.service.runApplication")
    val port = config.getInt("wp.service.port")
    val maxWarmup = getFiniteDuration("wp.service.maxWarmup", config)
    val waitEnableLB = getFiniteDuration("wp.service.waitEnableLB", config)
    if (runServer) {
      server = Server(sd = this, handler = handler,
        queryStringHandlerIn = queryStringHandler,
        listen = listen, port = port, isDev, useOld, buildInfo)
      ok = server.start()
    }
    if (!ok) {
      system.shutdown()
      system.awaitTermination()
      sys.exit(-1)
    }
    if (runApplication) {
      doApplication(system)
      //log.info(noId, "Application ready")
    }
    if (runWarmup || lcInfo != None) {
      lcInfo map {
        case info => info.lcActor ! new LcWarming(maxWarmup)
      }
      //log.info(noId, "Starting warmup")
      LoggingControl.setWarmup(true)
      try {
        def progress(percent: Int) {
          lcInfo map {
            case info => info.lcActor ! LcWarmupPercent(percent)
          }
        }
        val warmupFuture = handler.warmup(progress)
        Await.result(warmupFuture, maxWarmup)
        //log.info(noId, "Finished warmup")
      } catch {
        case ex: Throwable =>
          log.error(noId, "Warmup failed", ex)
      }
      LoggingControl.monitor0 ! Reset
      LoggingControl.setWarmup(false)
    }
    lcInfo map {
      case info =>
        info.lcActor ! LcWarmed
        try {
          Await.result(info.onLB, waitEnableLB)
        } catch {
          case ex: Throwable =>
            log.error(noId, "No onLB received from agent")
        }
    }
    lcInfo map {
      case info => info.lcActor ! LcUp
    }
  }

  private def stopService(lcInfo: Option[LcInfo] = None) {
    val waitDisableLB = getFiniteDuration("wp.service.waitDisableLB", config)
    val waitDrain = getFiniteDuration("wp.service.waitDrain", config)
    val waitStopService = getFiniteDuration("wp.service.waitStopService", config)
    lcInfo map {
      case info =>
        try {
          try {
            Await.result(info.offLB, waitDisableLB)
          } catch {
            case ex: Throwable =>
              log.error(noId, "No offLB received from agent")
          }
          info.lcActor ! LcDraining
          // mark server unavailable
          if (server != null) server.drain()
          val p = Promise[Unit]()
          if (LoggingControl.monitor0 != null) {
            // wait for q to drain
            LoggingControl.monitor0 ! Drained(p)
            Await.result(p.future, waitDrain)
          }
          info.lcActor ! LcDrained(true)
        } catch {
          case ex: Throwable =>
            log.warn(noId, "Drain queue not empty", ex)
            info.lcActor ! LcDrained(false)
        }
    }
    try {
      if (handler != null) {
        val closeFuture = handler.close()
        Await.result(closeFuture, waitStopService)
      }
    } catch {
      case ex: Throwable =>
        log.error(noId, "Handler close failed", ex)
    }
  }

  private def stopSystem() {
    val waitStopSystem = getFiniteDuration("wp.service.waitStopSystem", config)
    if (server != null) server.stop
    if (LoggingControl.monitor0 != null) {
      // TODO wait for pending messages  to logger???
      val f1 = gracefulStop(LoggingControl.monitor0, 2 minutes)
      Await.result(f1, 2 minutes)
      LoggingControl.monitor0 = null
    }
    Thread.sleep(50) // Fix testSOLR client log msg after shutdown
    val f = LoggingControl.stop()
    Await.result(f, waitStopSystem)
    system.shutdown()
    system.awaitTermination()
    LoggingControl.system0 = null
    LoggingControl.serviceName0 = ""
  }

  private def start(debugConfig: Option[Config] = None) {
    startSystem(debugConfig, true)
    startService(true)
  }

  private def readCmds() {
    while (true) {
      val line = scala.io.StdIn.readLine
      //val line = Console.readLine()
      if (line == null) {
        println("command line input not enabled")
        return
      }
      println("SAW " + line)
      if (line == "stop") {
        println("Stopping...")
        return
      } else {
        println("Unrecognized command: " + line)
      }
    }
  }


  /**
   * This method is only used by the jsvc daemon and should never be called directly.
   *
   * @param context  the jsvc daemon context
   */
  def init(context: DaemonContext) {
    val args = context.getArguments
  }

  /**
   * This method is only used by the jsvc daemon and should never be called directly.
   */
  def start() = {
    startSystem(None, false)
    log.info(noId, "Starting jsvc service")
    startService(false)
  }

  /**
   * This method is only used by the jsvc daemon and should never be called directly.
   */
  def stop() {
    log.info(noId, "Stopping jsvc service")
    stopSystem()
  }

  /**
   * This method is only used by the jsvc daemon and should never be called directly.
   */
  def destroy() {
  }

  /**
   * This is the basic method for running a service during development
   *
   * @param debugConfig  an optional override to the config. This is useful for testing.
   *
   * @param readCommands  an optional boolean. When true it allows sbt to read a stop
   *                      command for stopping the service.
   */
  def runServer(debugConfig: Option[Config] = None, readCommands: Boolean = true) {
    startSystem(debugConfig, true)
    startService(true)
    // TODO shutdown when not reading commands??
    if (readCommands) {
      readCmds()
      log.info(noId, "Stopping service")
      stopService()
      stopSystem()
    }
  }

  /**
   *
   * This method is used for testing when no server is needed, but the test requires
   * configuration, logging, and/or monitoring.
   *
   * @param clientHandler the client callback.
   *
   * @param debugConfig an optional override to the config. This is useful for testing.
   */
  def runClient(clientHandler: ClientCallback, debugConfig: Option[Config] = None) {
    startSystem(debugConfig, true, isTest = true)
    try {
      clientHandler.act(ClientCallback.Info(system))
    } catch {
      case ex: Throwable =>
        //log.error(noId, "Client failed", ex)
        // Must rethrow the exception for ScalaTest!
        throw ex
    } finally {
      stopSystem()
    }
  }

  /**
   * This method is used for client server testing, where the HTTP interface of the server
   * is called via client code.
   * Once the server is started the client callback is called.
   * When the callback returns the server is shut down.
   *
   * @param clientHandler the client callback.
   *
   * @param debugConfig an optional override to the config. This is useful for testing.
   */
  def runClientServer(clientHandler: ClientCallback, debugConfig: Option[Config] = None) {
    startSystem(debugConfig, true, isTest = true)
    startService(true)
    try {
      clientHandler.act(ClientCallback.Info(system))
    } catch {
      case ex: Throwable =>
        //log.error(noId, "Client failed", ex)
        // Must rethrow the exception for ScalaTest!
        throw ex
    } finally {
      stopService()
      stopSystem()
    }
  }

  /**
   * This method is used for testing using ScalaTest Before/After.
   * It should be called in the before method and testAfter should be called in the after method.
   * It handles setup of config, logging and monitoring.
   * @param debugConfig an optional override to the config.
   * @return  info for the tests.
   */
  def testBefore(debugConfig: Option[Config] = None): ClientCallback.Info = {
    startSystem(debugConfig, true, isTest = true)
    ClientCallback.Info(system)
  }


  /**
   * This method is used for testing using ScalaTest Before/After.
   * It should be called in the before method and testAfter should be called in the after method.
   * It handles setup of config, logging and monitoring and starts the service (server and application).
   * @param debugConfig an optional override to the config.
   * @return  info for the tests.
   */
  def testBeforeSystem(debugConfig: Option[Config] = None): ClientCallback.Info = {
    startSystem(debugConfig, true, isTest = true)
    startService(true)
    ClientCallback.Info(system)
  }

  /**
   * This method is used for testing using ScalaTest Before/After.
   * It should be called in the after method when calling testBefore or testBeforeServer in
   * the before method.
   */
  def testAfter() {
    stopService()
    stopSystem()
  }

  private[framework] def startDocker(select: Seq[String], dockerInfo0: JsonObject, isDev: Boolean = false) {
    dockerInfo = dockerInfo0
    startSystem(None, isDev, isDocker = true, select = select)
  }

  private[framework] def runDocker(lcInfo: LcInfo, isDev: Boolean = false) {
    startService(isDev, lcInfo = Some(lcInfo))
  }

  private[framework] def stopDocker(lcInfo: LcInfo) {
    stopService(lcInfo = Some(lcInfo))
    stopSystem()
  }


}
*/

