package com.whitepages.framework.util

import akka.actor.{ActorContext, ActorSystem, ActorRefFactory}
import com.whitepages.framework.logging.{RequestId, AnyId}
import java.util.UUID
import com.typesafe.config.Config

/**
 * A object containing framework utility methods.
 */
object Util extends ClassSupport {

  /**
   * Given an actorFactory (ActorSystem or ActorContext) return the ActorSystem.
   * This allows us to no have to pass an ActorSystem in addition to an ActorFactory.
   * @param actorFactory the actorFactory.
   * @return the underlying ActorSystem.
   */
  @deprecated("","") def getSystem(actorFactory: ActorRefFactory): ActorSystem = {
    actorFactory match {
      case s: ActorSystem => s
      case c: ActorContext => c.system
    }
  }


  // Use RequestId constructor instead
  @deprecated("","") def getId(id: AnyId): RequestId = {
    id match {
      case id: RequestId => id
      case _ => RequestId(UUID.randomUUID().toString, "0")
    }
  }

  // TODO deprecate after config changes
  @deprecated("","") def mkConfigPath(path: Seq[String]) = path.mkString(".")

  // TODO deprecate after config changes
  @deprecated("","") def mkWpClientConfigSeq(clientName: String) = {
    Seq("wp", serviceName, "clients", clientName)
  }

  // TODO deprecate after config changes
  @deprecated("","") def getClientConfig(config: Config,  clientName: String) = {
    config.getConfig(mkConfigPath(mkWpClientConfigSeq(clientName)))
  }


}
