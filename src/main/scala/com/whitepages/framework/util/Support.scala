package com.whitepages.framework.util

import com.whitepages.framework.logging.{RequestId, LoggingControl}
import akka.actor.ActorSystem
import scala.util.Random
import com.typesafe.config.Config
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

private[framework] trait Support {
  /**
   * The framework monitor
   */
  protected def monitor = LoggingControl.monitor0

  /**
   * The service name.
   */
  protected def serviceName = LoggingControl.serviceName0

  /**
   * the actor system used throughout the framework.
   */
  protected implicit def system: ActorSystem = LoggingControl.system0

  /**
   * The system configuration.
   */
  protected lazy val config = system.settings.config

  /**
   * The if of the host the service is running on.
   */
  protected def hostIp = LoggingControl.hostIp0

  /**
   * The name of the host the service is running on.
   */
  protected def hostName = LoggingControl.hostName0

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

  /**
   * This object contains methods used to mark regions to be timed in the timing log.
   */
  protected object time {
    /**
     * Call this method to start timing a region of code for the time log.
     * @param id the request id to be timed.
     * @param name each region to be timed must have a different name.
     * @param uid optional, if the region is executed multiple times, each occurance must have a different uid.
     */
    def start(id: RequestId, name: String, uid: String = "") {
      if (LoggingControl.doTime) LoggingControl.timeStart(id, name, uid)
    }

    /**
     * Call this method to end timing a region of code for the time log.
     * The id, name, and uid must match the corresponding start.
     * @param id the request id to be timed.
     * @param name each region to be timed must have a different name.
     * @param uid optional, if the region is executed multiple times, each occurance must have a different uid.
     */
    def end(id: RequestId, name: String, uid: String = "") {
      if (LoggingControl.doTime) LoggingControl.timeEnd(id, name, uid)
    }
  }

  /**
   * This method wraps a section of code, so that timings for it will appear in the time log.
   * @param id the requestId to be timed.
   * @param name  each region to be timed must have a different name.
   * @param body  the code to be timed.
   * @tparam T  the result type of the body.
   * @return  the value of the body. If the body throws an exception than that exception will pass through the Time call.
   */
  protected def Time[T](id: RequestId, name: String)(body: => T): T = {
    if (LoggingControl.doTime) {
      val uid = Random.nextLong().toHexString
      LoggingControl.timeStart(id, name, uid)
      try {
        body
      } finally {
        LoggingControl.timeEnd(id, name, uid)
      }
    } else {
      body
    }
  }
}
