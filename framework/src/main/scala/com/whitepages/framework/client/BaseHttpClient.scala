package com.whitepages.framework.client

import scala.concurrent.Future
import spray.http.HttpRequest
import spray.http.HttpResponse
import com.whitepages.framework.logging.ReqIdAndSpanOut


private[client] trait BaseHttpClientLike {
  def call(mapper: ClientLoggingMapper[HttpRequest, HttpResponse])
          (request: HttpRequest,
           id: ReqIdAndSpanOut,
           percent: Int,
           timing: Option[Any],
           logExtra: Option[Any]): Future[HttpResponse]

  def stop: Future[Unit]
}

