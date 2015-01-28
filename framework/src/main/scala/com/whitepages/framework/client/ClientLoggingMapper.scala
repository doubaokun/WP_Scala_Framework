package com.whitepages.framework.client

import com.persist.JsonOps._
import spray.http.{HttpRequest,HttpResponse}
//import com.whitepages.framework.client.RedisClient.{RedisResponse, RedisRequest}

//TODO: add jToIn for log replay
private[whitepages] trait ClientLoggingMapper[In, Out] {
  def inToJ(in: In): JsonObject
  //def jToIn(j: Json): only necessary for log replay, not regular logging or manual mock insertion
  def outToJ(out: Out): Json
  def jToOut(j: Json): Out
}

private[whitepages] trait ClientHttpLoggingMapper extends ClientLoggingMapper[HttpRequest, HttpResponse]

//private[whitepages] trait ClientRedisLoggingMapper extends ClientLoggingMapper[RedisRequest, RedisResponse]

