package com.whitepages.framework.service

import akka.actor.ActorRefFactory
import com.persist.JsonOps._
import com.typesafe.config.Config
import com.whitepages.framework.logging.{RequestId, AnyId}
import com.whitepages.framework.server.{SprayServer, BaseServer}
import spray.http.{HttpResponse, HttpRequest}
import scala.concurrent.{Promise, Future}

private[framework] object SprayService {

  case class SprayIn(request: HttpRequest, dyn: JsonObject, opts: JsonObject, id: RequestId)

  // TODO in and out should be passed byname?
  case class SprayOut(response: HttpResponse, cmd: String, in: Json, out: Json, extras: JsonObject)

}

private[framework] trait SprayService extends BaseService {

  import SprayService._

  private[framework] def sprayAct(in: SprayIn): Future[Option[SprayOut]]

  private[framework] def createService(factory: ActorRefFactory, serviceConfig: Config, isDev: Boolean, buildInfo: JsonObject, ready: Promise[Boolean]): BaseServer = {
    new SprayServer(factory, serviceConfig, this, isDev, buildInfo, ready)
  }


}
