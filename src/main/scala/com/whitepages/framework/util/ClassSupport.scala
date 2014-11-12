package com.whitepages.framework.util

import org.slf4j.LoggerFactory
import com.whitepages.framework.logging.{RequestId, LoggingControl, Logger}
import akka.actor.ActorSystem
import scala.util.Random
import com.typesafe.config.Config
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

/**
 * This trait should be included in Scala (non-actor) classes to enable logging
 * and monitoring. Click the visibility All button to see protected
 * members that are defined here.
 */
trait ClassSupport extends Support {
  private[this] val className = getClass.getName
  private[this] lazy val oldLog = LoggerFactory.getLogger(className)
  //private[this] lazy val oldLogger = OldLogger(oldLog)
  /**
   * The framework logger.
   */
  protected lazy val log = Logger(className = Some(className)) //, oldLogger = oldLogger)


}
