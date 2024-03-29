package com.whitepages.framework.server

import com.whitepages.framework.service.{BaseHandler, BaseService}
import akka.actor.{Props, ActorRef}
import com.persist.JsonOps._
import scala.concurrent.{Await, Promise}
import akka.util.Timeout
import scala.concurrent.duration._
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.logging.noId
import scala.language.postfixOps

private[framework] case class Server(sd: BaseService,
                                     //infos: Map[String, Info],
                                     handler: BaseHandler,
                                     queryStringHandlerIn: Option[(JsonObject, String) => JsonObject],
                                     listen: String, port: Int, isDev: Boolean,
                                      buildInfo:Json) extends ClassSupport {

  private[this] var serverActor: ActorRef = null
  private[this] val unbindCompletedPromise = Promise[Boolean]

  /** Start the service
    *
    * @return True if service was started successfully, else false.
    */
  def start(): Boolean = {
    implicit val timeout: Timeout = 5 seconds
    val bindCompletedPromise = Promise[Boolean]

    val queryStringHandler = queryStringHandlerIn.getOrElse(DefaultQueryStringHandler.handle)
    val logRequest = config.getBoolean("wp.service.logRequest")
    val logResponse = config.getBoolean("wp.service.logResponse")

    serverActor = system.actorOf(Props(classOf[ServerActor], sd, bindCompletedPromise, unbindCompletedPromise, //infos,
      handler, queryStringHandler, isDev, logRequest, logResponse, buildInfo), name = "server")

    serverActor ! ServerActor.Bind(listen, port)

    val f = bindCompletedPromise.future
    val s = Await.result(f, getFiniteDuration("wp.service.startUpWait"))

    s
  }

  def drain() {
    if (serverActor != null) {
      serverActor ! ServerActor.Drain
    }
  }

  /** Stop the service
    *
    * This will unbind the service from the listening address, and stop the actor system as provided in the
    * constructor.
    */
  def stop {
    log.debug(noId, "Shutting down http server")

    if (serverActor != null) {
      serverActor ! ServerActor.Unbind

      // Given the unbind a chance to complete before we stop the actor system
      val f = unbindCompletedPromise.future
      Await.ready(f, 10.seconds)
    }
  }
}
