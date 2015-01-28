package com.whitepages.framework.service

import akka.actor.{ActorRefFactory, ActorRef}
import scala.concurrent.Future
import com.persist.JsonOps.{JsonObject, Json}
import com.whitepages.framework.logging.RequestId

/*
/**
 * A request to a Json service.
 *
 * @param cmd  the command name.
 * @param request  the Json request.
 * @param requestId the request id (for logging).
 * @param dyn dynamic properties.
 * @param method  the HTTP method (GET Or POST).
 */
case class JsonHandlerRequest(cmd: String, request: Json, requestId: RequestId, dyn:JsonObject, method:String)

/**
 * The response from a Json service request.
 *
 * @param response the Json response.
 * @param logItems  optional additional per service logging.
 * @param monitor optional additional per service monitoring.
 */
case class JsonHandlerResponse(response: Json, logItems: Map[String, Any] = Map(), monitor: Option[Any] = None)

/**
 * Request handlers for Json services should include this trait.
 */
trait JsonHandler extends BaseHandler {

  /**
   * This method is called for each request.
   *
   * @param request the request.
   *
   * @return  a future of the response.
   */
  def act(request: JsonHandlerRequest): Future[JsonHandlerResponse]

}

/**
 * Request handler factories should implement this trait for
 * Json services.
 */
trait JsonHandlerFactory extends BaseHandlerFactory {

  /**
   * This method is called once at service startup to create a request handler.
   *
   * @param factory the factory for creating new Actors.
   *
   */
  def start(factory: ActorRefFactory): JsonHandler

}
*/

