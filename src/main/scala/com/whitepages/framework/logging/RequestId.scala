package com.whitepages.framework.logging

import java.util.UUID
import scala.util.Random

/**
 * The trait for logging ids. It can be either a RequestId io a specific request or
 * NoId when there is no associated request.
 */
trait AnyId

/**
 * The logging id of a specific request. Request ids are usually created automatically by
 * the framework server.
 * @param trackingId the global unique id of the request,
 *                   optional: A new unique id will be created if this is not specified
 * @param spanId a sub-id used when a a service is called multiple times for the same global request.
 *               optional: defaults to 0
 * @param level a field for controlling per request log levels.
 *              optional
 */
case class RequestId(trackingId: String = UUID.randomUUID().toString
                     , spanId: String = "0"
                     , level: Option[Int] = None) extends AnyId


/**
 * The id value used in logging calls when there is no associated request.
 */
case object noId extends AnyId

private[framework] class ReqIdAndSpanOut(val requestId: RequestId, val spanIdOut: String)

private[framework] object ReqIdAndSpanOut {
  def defaultSpanId = Random.nextLong().toHexString

  def defaultRequestId(id: AnyId): RequestId = {
    id match {
      case rid: RequestId => rid
      case noId => RequestId()
    }
  }

  def apply(id: AnyId, spanIdOut: String = defaultSpanId) = new ReqIdAndSpanOut(defaultRequestId(id), spanIdOut)
}






