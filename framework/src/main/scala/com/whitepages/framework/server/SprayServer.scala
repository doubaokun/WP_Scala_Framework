package com.whitepages.framework.server

import akka.actor.{ActorRefFactory, Props}
import akka.util.Timeout
import com.whitepages.framework.logging.noId
import com.whitepages.framework.service.SprayService
import com.typesafe.config.Config
import com.persist.JsonOps._
import com.whitepages.framework.util.ClassSupport
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.{Await, Promise}

private[framework] class SprayServer(factory: ActorRefFactory, serviceConfig: Config, service: SprayService, isDev: Boolean, buildInfo: JsonObject,
                  ready: Promise[Boolean]) extends BaseServer with ClassSupport {
  private[this] implicit val timeout: Timeout = 5 seconds
  private[this] val bindCompletedPromise = Promise[Boolean]
  private[this] val unbindCompletedPromise = Promise[Boolean]

  private[this] val port = serviceConfig.getInt("port")
  private[this] val listen = serviceConfig.getString("listen")

  val serverActor = system.actorOf(Props(classOf[SprayServerActor], service, serviceConfig, bindCompletedPromise, unbindCompletedPromise,
    service, isDev, buildInfo), name = "server")

  serverActor ! SprayUtil.Bind(listen, port)

  val f = bindCompletedPromise.future
  try {
    val s = Await.result(f, getFiniteDuration("startUpWait", config = serviceConfig))
    ready.success(true)
  } catch {
    case ex: Throwable =>
      log.error(noId, JsonObject("msg" -> "service startup failed", "service" -> service.serviceName), ex)
      ready.success(false)
  }

  def drain {
    if (serverActor != null) {
      serverActor ! SprayUtil.Drain
    }
  }

  /** Stop the service
    *
    * This will unbind the service from the listening address, and stop the actor system as provided in the
    * constructor.
    */
  def stop {
    log.debug(noId, "Shutting down spray http server")

    if (serverActor != null) {
      serverActor ! SprayUtil.Unbind

      // Given the unbind a chance to complete before we stop the actor system
      val f = unbindCompletedPromise.future
      Await.ready(f, 10.seconds)
    }
  }
}

