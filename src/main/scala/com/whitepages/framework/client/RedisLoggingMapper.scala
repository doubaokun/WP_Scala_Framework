package com.whitepages.framework.client

import com.persist.JsonOps._
import com.whitepages.framework.client.RedisClient._
import com.whitepages.framework.logging.{RequestId, noId}
import com.whitepages.framework.util.ClassSupport
import scala.language.postfixOps

private[client] case class RedisLoggingMapper(clientName: String) extends ClientRedisLoggingMapper with ClassSupport {
  /**
   * TODO RedisLoggingMapper IS NOT TO SPEC. IT WORKS FOR LOGGING ONLY. IT DOES NOT WORK FOR MOCKING
   */
  override def inToJ(in: RedisRequest): JsonObject = {
    in match {
      //TODO flesh out for other input types
      case x: RedisGet => JsonObject("client" -> clientName,
                "request" ->
                  JsonObject(
                              "cmd" -> x.cmd.toString,
                              "key" -> x.data.key,
                              "requestId" -> x.requestId.toString
                            )
              )
      case x: RedisSet =>
        val cmd = x.cmd.toString
        JsonObject("client" -> clientName,
                                      "request" ->
                                        JsonObject(
                                                    "cmd" -> cmd.toString,
                                                    "key" -> x.data.key,
                                                    "requestId" -> x.requestId.toString
                                                  )
                                    )
      case x: Any => log.error(noId,s"Unknown Redis command while logging: $x" )
        JsonObject()
    }
  }

//  Client Log formatter
  //TODO flesh out for other response types

  override def outToJ(out: RedisResponse) = {
    out match {
      case x: RedisResponseData => JsonObject(
                "data" -> x.data.toString,
                "requestId" -> x.requestId.toString
              )
      case x: Any => JsonObject("data" -> x.toString)
    }
  }

  override def jToOut(j: Json) = {
    val data = "NOT IN USE RedisLoggingMapper.jToOut"

    RedisResponseData(RequestId("foo","foo"), RedisDataKeyValue("foo", data))
  }

}
