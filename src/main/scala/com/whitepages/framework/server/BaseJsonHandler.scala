package com.whitepages.framework.server

import com.persist.JsonOps.{JsonObject, Json}
import scala.concurrent.Future
import com.whitepages.framework.logging.RequestId

private[server] trait BaseJsonHandler {

  def doJson(id: RequestId, cmd: String, jrequest: => Json, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)]

  def doJsonString(id: RequestId, cmd: String, requests: String, dyn: JsonObject, method:String): Future[(String, Json, Json, JsonObject)]

}
