package com.whitepages.framework.service

/*
import com.whitepages.framework.util.Thrift
import com.twitter.scrooge.ThriftStruct
import com.persist.JsonOps._
import com.whitepages.framework.logging.RequestId
import scala.concurrent.Future
import akka.actor.ActorRefFactory

object ThriftService {

  /**
   * A request to a Thrift service.
   *
   * @param cmd  the command name.
   * @param request  the Thrift request.
   * @param requestId the request id (for logging).
   * @param dyn dynamic properties.
   */
  case class Request(cmd: String, request: ThriftStruct, requestId: RequestId, dyn: JsonObject)

  /**
   * The response from a Thrift service request.
   *
   * @param response the Thrift response.
   * @param logItems  optional additional per service logging.
   * @param monitor optional additional per service monitoring to be added to the access log.
   */
  case class Response(response: ThriftStruct, logItems: Map[String, Any] = Map(), monitor: Option[Any] = None)

  /**
   * Request handlers for Thrift services should include this trait.
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
   * Thrift services.
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
 * This abstract class should extended to build the top-level of a Thrift (with Json) service.
 **/
abstract class ThriftService extends BaseService {

  /**
   * The package containing the Scrooge generated Scala code for Thrift.
   * This will be supplied by the service class.
   */
  val thriftPath: String

  /**
   * The name of the Thrift service that defines the set of Thrift commands.
   * This will be supplied by the service class.
   */
  val thriftName: String

  val handlerFactory: ThriftService.HandlerFactory

  private[this] lazy val info = Thrift.info(thriftPath, thriftName)

  /**
   * Info about the service Thrift data structures that can be used to
   * serialization/deserialization of the Thrift.
   *
   * This should go away after the Scrooge rewrite.
   *
   * @return the Thrift info.
   */
  def getInfo = info
}
*/
