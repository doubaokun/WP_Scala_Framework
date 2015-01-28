package com.whitepages.framework.logging

import akka.actor.Actor

/**
 * This trait should be included in Akka Actors to enable logging.
 * Click the visibility All button to see protected
 * members that are defined here.
 * You might also want to un-click the Actor button.
 */
trait ActorLogging extends Actor {
  private[this] val className = getClass.getName
  private[this] val actorName = self.path.toString()

  /**
   * The framework logger.
   */
  protected lazy val log = Logger(className = Some(className), actorName = Some(actorName))

}
