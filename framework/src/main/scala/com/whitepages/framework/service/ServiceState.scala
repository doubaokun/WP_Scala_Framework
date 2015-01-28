package com.whitepages.framework.service

import akka.actor.{ActorSystem, ActorRef}

private[framework] object ServiceState {

  private[framework] var monitor0: ActorRef = null

  private[framework] var serviceName0: String = ""

  private[framework] var hostName0: String = ""

  private[framework] var hostIp0: String = ""

  private[framework] var system0: ActorSystem = null

  private[framework] var service: BaseService = null

  private[service] var globalPercent: Int = 100
  private[service] var warmupPercent = 100
  private[service] var warmup: Boolean = false

  private[framework] def getPercent = {
    if (warmup) warmupPercent else globalPercent
  }


}
