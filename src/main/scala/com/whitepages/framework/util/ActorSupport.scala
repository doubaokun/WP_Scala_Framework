package com.whitepages.framework.util

import akka.actor.{ActorSystem, Actor}
import org.slf4j.LoggerFactory
import com.whitepages.framework.logging.{RequestId, LoggingControl, Logger}
import scala.util.Random


/**
 * This trait should be included in Akka Actors to enable logging
 * and monitoring. Click the visibility All button to see protected
 * members that are defined here.
 * You might also want to un-click the Actor button.
 */

trait ActorSupport extends Actor with Support {
  private[this] val className = getClass.getName
  private[this] val actorName = self.path.toString()
  private[this] lazy val oldLog = LoggerFactory.getLogger(className)
  //private[this] lazy val oldLogger = OldLogger(oldLog)
  /**
   * The framework logger.
   */
  protected lazy val log = Logger(className = Some(className), actorName = Some(actorName)) // , oldLogger = oldLogger)

}

