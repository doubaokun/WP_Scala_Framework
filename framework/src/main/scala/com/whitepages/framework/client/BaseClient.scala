package com.whitepages.framework.client

import scala.concurrent.Future
import com.whitepages.framework.monitor.Monitor
import scala.language.postfixOps
import com.whitepages.framework.exceptions.{NotAvailableException, ClientTimeoutException}
import com.whitepages.framework.logging.AnyId
/*
// TODO add a BUSY alternative
private[client] case class ClientRetry(msg: String, t: Throwable = null) extends Exception(msg, t)

private[client] case class NoConnect(msg: String, t: Throwable = null) extends Exception(msg, t)
*/

private[client] trait BaseClientTrait[In, Out] {
  def call(in: In, id: AnyId, percent: Int): Future[Out]

  def stop:Future[Unit]
}

