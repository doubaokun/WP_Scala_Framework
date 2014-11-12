package com.whitepages.framework.service

import scala.concurrent.Future
import akka.actor.ActorRefFactory

private[framework] trait BaseHandler {

  /**
   * This method is called to warm up a service.
   * It is only called when config wp.service.runWarmup is true.
   * The default behavior is a noop. Override it
   * to install custom code.
   * @param progress callback to report progress. Value should be a percent of completion in range 0..100.
   * @return future that completes when warmup is done.
   */
  def warmup(progress: (Int) => Unit): Future[Unit] = Future.successful(())

  /**
   * This method is used to start an application that is not primarily
   * based on an HTTP server. It is called only when config
   * wp.service.runApplication is true. If no HTTP server is needed then
   * also set wp.service.runServer to false.
   * The default behavior is a noop. Override it
   * to install custom code.
   * @param refFactory  future that completes when application has been started.
   * @return
   */
  def startApplication(refFactory: ActorRefFactory): Future[Unit] = Future.successful(())

  /**
   * This method is called to close a handler including both the HTTP and
   * application parts.
   * The default behavior is a noop. Override it
   * to install custom code.
   * @return  a future that is complete when everything has been closed.
   */
  def close(): Future[Unit] = Future.successful(())
}

private[whitepages] trait BaseHandlerFactory {
  def start(refFactory: ActorRefFactory): BaseHandler
}
