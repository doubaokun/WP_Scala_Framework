package com.whitepages.framework.service

import akka.actor.{ActorRef, ActorRefFactory}

object ClientCallback {

  /**
   * The info passed to client code for runClient or runClientServer.
   *
   * @param refFactory the actorRefFactory for creating new Actors.
   *
   **/
  case class Info(refFactory: ActorRefFactory)

}

/**
 * Instances of this trait are passed to runClientServer of runClient.
 */
trait ClientCallback {

  /**
   * This method is the callback for running client code.
   *
   * @param info service info passed to the client.
   */
  def act(info: ClientCallback.Info)

}

