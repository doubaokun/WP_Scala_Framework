package com.whitepages.framework.service

import com.persist.JsonOps._
import com.whitepages.framework.logging.RequestId
import com.whitepages.framework.server.JsonAct
import scala.concurrent.Future
import akka.actor.ActorRefFactory

object JsonService {

  /**
   * A request to a Json service.
   *
   * @param cmd  the command name.
   * @param request  the Json request.
   * @param requestId the request id (for logging).
   * @param dyn dynamic properties.
   * @param method  the HTTP method (GET Or POST).
   */
  case class Request(cmd: String, request: Json, requestId: RequestId, dyn: JsonObject, method: String)

  /**
   * The response from a Json service request.
   *
   * @param response the Json response.
   * @param logItems  optional additional per service logging.
   * @param monitor optional additional per service monitoring to be added to the access log.
   */
  case class Response(response: Json, logItems: Map[String, Any] = Map(), monitor: Option[Any] = None)

  /**
   * Request handlers for Json services should include this trait.
   */
  trait Handler extends BaseHandler {

    /**
     * This method is called for each request.
     *
     * @param request the request.
     *
     * @return  a future of the response.
     */
    def act(request: Request): Future[Response]

  }


  /**
   * Request handler factories should implement this trait for
   * Json services.
   */
  trait HandlerFactory extends BaseHandlerFactory {

    /**
     * This method is called once at service startup to create a request handler.
     *
     * @param refFactory the factory for creating new Actors.
     *
     */
    def start(refFactory: ActorRefFactory): Handler

  }

}

/**
 * This abstract class should extended to build the top-level Json service.
 **/
abstract class JsonService extends SprayService {
  import SprayService._
  val handlerFactory: JsonService.HandlerFactory
  private[this] var jsonAct: JsonAct = null

  private[framework] def createHandler(factory: ActorRefFactory): BaseHandler = {
    val handler = handlerFactory.start(factory)
    jsonAct = JsonAct(factory, this, handler)
    handler
  }

  private[framework] def sprayAct(in: SprayIn): Future[Option[SprayOut]] =  jsonAct.sprayAct(in)
}

