package com.whitepages.framework.monitor

import com.codahale.metrics._
import com.codahale.metrics.graphite._
import java.net.InetSocketAddress
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport

private[monitor] case class MonitorGraphite(metrics: MetricRegistry)
  extends ReporterWrapper with ClassSupport {
  //val config = system.settings.config
  private[this] val graphiteConfigPath = "wp.monitoring.graphite"
  private[this] val graphiteHost = config.getString(graphiteConfigPath + ".graphiteHost")
  private[this] val graphitePort = config.getInt(graphiteConfigPath + ".graphitePort")
  private[this] val graphiteRateUnit = getTimeUnit(config.getString(graphiteConfigPath + ".rateUnit"))
  private[this] val graphiteDuration = getTimeUnit(config.getString(graphiteConfigPath + ".duration"))

  val reportUnit = getTimeUnit(config.getString(graphiteConfigPath + ".reportUnit"))
  val reportInterval = config.getInt(graphiteConfigPath + ".reportInterval")

  val reporterOpt: Option[GraphiteReporter] = {
    // path prefix should be of the form
    // env.service-name.host
    // qa59.asc-svc.ascsvc0
    // or for dev
    // dev.developer-laptop.service-name
    // dev.dansab0.asc-svc

    if (config.getBoolean(graphiteConfigPath + ".publishToGraphite")) {
      val graphitePathPrefix = getPathPrefix(serviceName)
      log.info(noId, s"Publishing metrics to Graphite; host = $graphiteHost:$graphitePort; prefix = $graphitePathPrefix every $reportInterval ${reportUnit}, did you send a monitor reset?")

      val reporter = GraphiteReporter.forRegistry(metrics)
        .prefixedWith(graphitePathPrefix)
        .convertRatesTo(graphiteRateUnit)
        .convertDurationsTo(graphiteDuration)
        .filter(MetricFilter.ALL)
        .build(new Graphite(new InetSocketAddress(graphiteHost, graphitePort)))
      Some(reporter)
    }
    else {
      //log.info(noId, "Metrics publishing to Graphite disabled")
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
