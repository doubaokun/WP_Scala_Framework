package com.whitepages.framework.monitor

/*
import com.codahale.metrics._
import com.librato.metrics._
import com.typesafe.config.Config
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport

private[monitor] case class MonitorLibrato(config: Config, metrics: MetricRegistry)
  extends ReporterWrapper with ClassSupport {
  private[this] val libratoConfigPath = configPathFor(serviceName, "librato")
  private[this] val libratoApiKey = config.getString(libratoConfigPath + ".apiKey")
  private[this] val libratoUserName = config.getString(libratoConfigPath + ".userName")

  val publish = config.getBoolean(libratoConfigPath + ".publishToLibrato")
  val reportUnit = getTimeUnit(config.getString(libratoConfigPath + ".reportUnit"))
  val reportInterval = config.getLong(libratoConfigPath + ".reportInterval")

  val reporterOpt: Option[LibratoReporter] = {
    // path prefix should be of the form
    // env.service-name.host
    // qa59.asc-svc.ascsvc0
    // or for dev
    // dev.developer-laptop.service-name
    // dev.dansab0.asc-svc

    if (config.getBoolean(libratoConfigPath + ".publishToLibrato")) {
      val libratoPathPrefix = getPathPrefix(serviceName)
      log.info(noId, s"Publishing metrics to Librato; prefix = $libratoPathPrefix")
      val reporter = LibratoReporter.builder(metrics, libratoUserName, libratoApiKey, libratoPathPrefix)
        .build()
      Some(reporter)
    }
    else {
      //log.info(noId, "Metrics publishing to Librato disabled")
      None
    }
  }

  def start() {
    reporterOpt.foreach(_.start(reportInterval, reportUnit))
  }

  def stop() {
    reporterOpt.foreach(_.stop())
  }

  def close() {
    reporterOpt match {
      case Some(r) => r.close()
      case None => /* nop */
    }
  }
}
*/
