package com.whitepages.framework.util

import com.whitepages.framework.logging.Timing
import akka.actor.ActorSystem
import com.whitepages.framework.service.ServiceState
import com.typesafe.config.Config
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

private[framework] trait Support extends Timing{
  /**
   * The framework monitor
   */
  protected def monitor = ServiceState.monitor0

  /**
   * The service name.
   */
  protected def serviceName = ServiceState.serviceName0

  /**
   * the actor system used throughout the framework.
   */
  protected implicit def system: ActorSystem = ServiceState.system0

  /**
   * The system configuration.
   */
  protected lazy val config = system.settings.config

  /**
   * The if of the host the service is running on.
   */
  protected def hostIp = ServiceState.hostIp0

  /**
   * The name of the host the service is running on.
   */
  protected def hostName = ServiceState.hostName0

  /**
   * Since Typesafe config is Java, it does not have an API that can produce
   * Scala FiniteDuration. This method provides this extra method.
   * @param path path in the system config to the duration value (if no units are specified milliseconds is assumed).
   * @param config an optional config to use. If not specified the top level system config will be used.
   * @return the value as a FiniteDuration.
   */
  protected def getFiniteDuration(path: String, config:Config=config): FiniteDuration = FiniteDuration(config.getDuration(path, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  private[framework] def getClientConfig(clientName: String): Config = {
    config.getConfig(s"wp.clients.${clientName}")
  }

}
