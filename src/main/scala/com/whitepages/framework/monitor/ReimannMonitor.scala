package com.whitepages.framework.monitor

import com.codahale.metrics.MetricRegistry
import com.whitepages.framework.util.ClassSupport
import com.codahale.metrics.riemann.{Riemann, RiemannReporter}
import com.whitepages.framework.logging.noId
import java.util.concurrent.TimeUnit
import com.codahale.metrics.riemann.RiemannReporter.Builder

private[monitor] case class RiemannMonitor(metrics: MetricRegistry)
  extends ReporterWrapper with ClassSupport {
  //private[this] val config = system.settings.config
  private[this] val riemannConfigPath = "wp.monitoring.riemann"
  private[this] val host = config.getString(riemannConfigPath + ".host")
  private[this] val port = config.getInt(riemannConfigPath + ".port")
  private[this] val environment = config.getString(riemannConfigPath + ".environment")
  private[this] val region = config.getString(riemannConfigPath + ".region")
  private[this] val localHost = "test123"
  private[this] val pollPeriod = 10
  private[this] val METRIC_SCHEMA_VERSION = "v1"
  private[this] val METRIC_SEPARATOR = "."
  private[this] val FLUME_AGENT = "flume"

  private[this] val reporterOpt: Option[RiemannReporter] = {
    if (config.getBoolean(riemannConfigPath + ".publish")) {
      try {
        val riemann = new Riemann(host, port)
        log.info(noId, "Initialized Riemann sink with host: " + host + ":" + port)

        val reporter = buildReporter(riemann, metrics)

        log.info(noId, "Initialized CodaHale Metrics RiemannReporter successfully")
        Some(reporter)
      } catch {
        case ex: Throwable =>
          log.error(noId, "Couldn't connect to Riemann server at: " + host + ":" + port)
          None
      }
    } else {
      None
    }
  }

  private def buildReporter(riemann: Riemann, metrics: MetricRegistry): RiemannReporter = {
    var tags = new java.util.ArrayList[String]()
    tags.add("service=" + FLUME_AGENT)
    tags.add("schema-version=" + METRIC_SCHEMA_VERSION)
    tags.add("env=" + environment)
    tags.add("region=" + region)

    val builder: Builder = RiemannReporter.forRegistry(metrics)
      .localHost(localHost)
      .withTtl(3.3f * pollPeriod)
      .useSeparator(METRIC_SEPARATOR)
      .tags(tags)

    builder.build(riemann)
  }

  def start() {
    reporterOpt.foreach(_.start(pollPeriod, TimeUnit.SECONDS))
  }

  def stop() = {
    reporterOpt.foreach(_.stop())
  }

  def close() = {
    reporterOpt match {
      case Some(r) => r.close()
      case None => /* nop */
    }
  }

}
