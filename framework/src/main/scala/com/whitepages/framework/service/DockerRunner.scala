package com.whitepages.framework.service

import akka.actor._
import com.typesafe.config.{ConfigValue, ConfigValueFactory, ConfigFactory}
import com.persist.JsonOps._
import scala.concurrent.{ExecutionContext, Await, Future, Promise}
import com.whitepages.framework.util.{ClassSupport, ActorSupport}
import com.whitepages.framework.logging.noId
import com.whitepages.framework.service.LifecycleMessages.LcStop
import scala.concurrent.duration.Duration.Inf
import scala.concurrent.duration._
import scala.language.postfixOps

// export agentHost= 192.168.59.3
// java -cp target/scala-2.11/test-classes:target/scala-2.11/scala-webservice-assembly-8.2.20.jar com.whitepages.framework.service.DockerRunner com.whitepages.jdaemon.JsonTestMain

private[framework] object LifecycleMessages {

  // From service
  trait LcMessage

  case object LcRunning extends LcMessage

  case class LcWarming(timeout: FiniteDuration) extends LcMessage

  case class LcWarmupPercent(percent: Int) extends LcMessage

  case object LcWarmed extends LcMessage

  case object LcUp extends LcMessage

  case object LcDraining extends LcMessage

  case class LcDrained(ok: Boolean) extends LcMessage

  case class LcStop() extends LcMessage

  case object LcInit extends LcMessage

  case class LcEvents(init: Promise[JsonObject] = Promise[JsonObject](),
                      onLB: Promise[Unit] = Promise[Unit](),
                      offLB: Promise[Unit] = Promise[Unit](),
                      stop: Promise[Unit] = Promise[Unit])

  // events to the service
  case class LcInfo(lcActor: ActorRef,
                    init: Future[JsonObject],
                    onLB: Future[Unit],
                    offLB: Future[Unit],
                    stop: Future[Unit])

  // events to the agent
  def AgentMessage(cmd: String, extra: JsonObject = emptyJsonObject) = Compact(JsonObject("cmd" -> cmd) ++ extra)


}

private[framework] class LifeCycleActor(agentActor: ActorSelection,
                                        lcEvents: LifecycleMessages.LcEvents,
                                        baseService: BaseService)
  extends Actor with ActorSupport {

  // TODO handle shutdown before startup is complete

  implicit val ec: ExecutionContext = this.context.dispatcher

  import LifecycleMessages._

  private[this] val lcInfo = LifecycleMessages.LcInfo(self,
    lcEvents.init.future,
    lcEvents.onLB.future,
    lcEvents.offLB.future,
    lcEvents.stop.future)

  // running. warming, warmed, up
  // stopping, draining, (drained or drainFailed), stopped
  private[this] var state: String = "initial"
  //log.info(noId, JsonObject("LCState" -> state))

  //lcInfo.lcActor ! LcInit
  lcInfo.lcActor ! LcRunning

  def doStop() {
    state = "stopping"
    agentActor ! AgentMessage("stopping")
    Future {
      baseService.stopDocker(lcInfo)
      state = "stopped"
      //log.info(noId, JsonObject("LCState" -> state))
      agentActor ! AgentMessage(state)
      lcEvents.stop.trySuccess(())
    }
  }

  private var warmPercent: Int = 0

  def receive = {
    // TODO move lcstop back to int handler
    case LcInit =>
      Future {
        baseService.runDocker(lcInfo)
      }
    case LcStop() => doStop()
    case LcWarmupPercent(percent) =>
      if (percent > warmPercent) {
        warmPercent = percent
        agentActor ! AgentMessage("warmPercent", JsonObject("percent" -> percent))
      }
    case m: LcMessage =>
      var extra: JsonObject = emptyJsonObject
      //log.error(noId, m.toString)
      m match {
        case LcRunning =>
          state = "running"
        case LcWarming(timeout) =>
          warmPercent = 0
          state = "warming"
          val to = timeout.toSeconds + 5
          extra = JsonObject("timeout" -> to)
        case LcWarmed =>
          state = "warmed"
        case LcUp =>
          state = "up"
        case LcDraining =>
          state = "draining"
        case LcDrained(ok: Boolean) =>
          state = if (ok) "drained" else "drainFailed"
        case x: Any =>
      }
      //if (state != "running") log.info(noId, JsonObject("LCState" -> state))
      agentActor ! AgentMessage(state, extra)
    case s: String =>
      val j = try {
        Json(s)
      } catch {
        case ex: Throwable => emptyJsonObject
      }
      val cmd = jgetString(j, "cmd")
      val request = jgetObject(j, "request")
      cmd match {
        case "init" => lcEvents.init.trySuccess(request)
        case "onlb" => lcEvents.onLB.trySuccess(())
        case "stop" => doStop()
        case "offlb" => lcEvents.offLB.trySuccess(())
        case "getstate" => agentActor ! AgentMessage(state)
        case x: Any =>
          log.error(noId, JsonObject("badLCcmd" -> x.toString))

      }

    case x: Any =>
    //println("lifecycle:" + x)
  }

}

private[framework] object DockerRunner extends ClassSupport {

  //  private[this] val x = new Object()
  private[framework] var enableSignals = false

  def main(args: Array[String]) {
    if (args.size > 0) {
      val isDev = args.size > 1

      // Find base service and agentHost
      val name = args.head
      val clazz = try {
        Class.forName(name)
      } catch {
        case ex: Throwable =>
          throw new Exception("Class not found:" + name)
      }
      val cons = clazz.getConstructors.head
      var baseService = cons.newInstance().asInstanceOf[BaseService]

      val serviceInfo = if (args.size > 1) {
        JsonObject("agentHost" -> args(1))
      } else {
        val sysInfo = System.getenv("serviceInfo")
        if (sysInfo == null) {
          emptyJsonObject
        } else {
          jgetObject(Json(sysInfo))
        }
      }

      val agentHost = {
        val h = jgetString(serviceInfo, "agentHost")
        if (h == "") throw new Exception("RUN DOCKER SERVICE FAILED: NO AGENT HOST")
        h
      }
      val containerHost = {
        jgetString(serviceInfo, "containerHost")
      }
      val lifecyclePort = {
        jgetInt(serviceInfo, "lifecyclePort")
      }


      // set up remote actor system on port 3xxxx
      val lcEvents = LifecycleMessages.LcEvents()
      var portConfig: ConfigValue = ConfigValueFactory.fromAnyRef(lifecyclePort, "dynamic docker remote actor port")
      var hostConfig: ConfigValue = ConfigValueFactory.fromAnyRef(containerHost, "dynamic docker remote actor host")
      val lifecycleConfig = ConfigFactory.load("lifecycle.conf").withValue("akka.remote.netty.tcp.port", portConfig)
        .withValue("akka.remote.netty.tcp.hostname", hostConfig)
      val system = ActorSystem("lifecycle", lifecycleConfig)

      // look up agent actor
      val agentActor = system.actorSelection(s"akka.tcp://service-agent@${agentHost}:8991/user/lifecycle")

      // start lifecycle actor
      val lcActor = system.actorOf(Props(classOf[LifeCycleActor], agentActor, lcEvents, baseService), name = "lifecycle")

      val (initOk, initInfo): (Boolean, JsonObject) = try {
        (true, Await.result(lcEvents.init.future, 15 seconds))
      } catch {
        case ex: Throwable =>
          (false, emptyJsonObject)
      }

      def fixSeq(s: String): Seq[String] = {
        if (s == "") {
          Seq[String]()
        } else {
          s.split(",").toSeq
        }
      }

      val select: Seq[String] = {
        if (args.size > 2) {
          fixSeq(args(2))
        } else if (jhas(serviceInfo, "configSelect")) {
          jgetArray(serviceInfo, "configSelect") map {
            jgetString(_)
          }
        } else if (jhas(initInfo, "configSelect")) {
          // backward compat, remove this eventually
          jgetArray(initInfo, "configSelect") map {
            jgetString(_)
          }
        } else {
          // If we were to continue here we would be running a mis-configured system.
          log.fatal(noId, "No select config available")
          throw new Exception("RUN DOCKER SERVICE FAILED: NO SELECT CONFIG")
        }
      }

      // start system
      val dockerInfo = JsonObject("docker" -> JsonObject("serviceInfo" -> serviceInfo, "initInfo" -> initInfo))
      baseService.startDocker(select, dockerInfo, isDev = isDev)

      if (!initOk) {
        if (isDev) {
          log.error(noId, "No init received from agent")
        } else {
          throw new Exception("NO INIT RECEIVED FROM AGENT")
        }
      }
      log.info(noId, JsonObject("msg" -> "remote actor system started", "agentHost" -> agentHost, "agentPort" -> 8991,
        "containerHost" -> containerHost, "containerPort" -> lifecyclePort))

      lcActor ! LifecycleMessages.LcInit


      // enable shutdown signal handlers
      // Signal handlers don't always work: Docker bug,
      // So we use explicit stop instead!
      if (enableSignals) handleSignal(lcActor)

      // shutdown
      Await.result(lcEvents.stop.future, Inf)
      lcActor ! PoisonPill
      system.shutdown()
      system.awaitTermination(1 minute)
    } else {
      throw new Exception("RUN DOCKER SERVICE FAILED: NO NAME")
    }

    def handleSignal(lcActor: ActorRef) {
      val sh = new sun.misc.SignalHandler {
        def handle(sig: sun.misc.Signal) {
          log.error(noId, "signal:" + sig.toString())
          lcActor ! LcStop()
        }
      }
      // TERM for testing ^C
      val s = new sun.misc.Signal("TERM")
      sun.misc.Signal.handle(s, sh)
      // INT for docker shutdown
      val s1 = new sun.misc.Signal("INT")
      sun.misc.Signal.handle(s1, sh)
      log.info(noId, "Signal handlers ready")
    }

  }
}
