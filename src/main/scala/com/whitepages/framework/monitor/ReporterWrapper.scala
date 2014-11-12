package com.whitepages.framework.monitor

import java.util.concurrent.TimeUnit
import java.net.InetAddress
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport

private[monitor] trait ReporterWrapper extends ClassSupport {

  def getTimeUnit(unit: String): TimeUnit = {
    unit.toLowerCase match {
      case "nanoseconds" => TimeUnit.NANOSECONDS
      case "microseconds" => TimeUnit.MICROSECONDS
      case "milliseconds" => TimeUnit.MILLISECONDS
      case "seconds" => TimeUnit.SECONDS
      case "minutes" => TimeUnit.MINUTES
      case "hours" => TimeUnit.HOURS
      case "days" => TimeUnit.DAYS
      case _ => throw new IllegalArgumentException(s"Unknown time unit $unit")
    }
  }

  //def configPathFor(serviceName: String, reporterName: String): String =
  //  List("wp", serviceName, "monitoring", reporterName).mkString(".")

  def getPathPrefix(serviceName: String): String = {
    var hostname = "search-unknown.dev"
    import scala.util.control.Exception

    //TODO this will be deprecated once the hostname is provided by ClassSupport
    Exception.catching(classOf[java.net.UnknownHostException]) // this is usually thrown when offline and off the VPN
      .withApply(t => log.error(noId, s"Unable to determine hostname in ${this.getClass}, using: $hostname")) {
      // This looks redundant, but they behave differently on OSX and Ubuntu.
      val canonicalHostname = InetAddress.getLocalHost.getCanonicalHostName
      val localHostName = InetAddress.getLocalHost.getHostName

      hostname =
        if (canonicalHostname.matches(".*[a-zA-Z].*") && canonicalHostname.split("[.]").length > 1) canonicalHostname
        else if (localHostName.split("[.]").length > 1)                                             localHostName
        else                                                                                        Seq(localHostName, "search-default.dev.pages").mkString(".")
    }
    //TODO End Deprecated code

    val p = hostname.replaceFirst("corp", "dev").split("[.]").toSeq.take(2).reverse
    val env = p(0)
    val group = serviceName
    // TODO fix region before deploying to other AWS regions
    val region = if (hostname.contains("corp")) "dev"
                 else if (hostname.contains("aws")) "us-west2a"
                 else "dc-sea0"
    val shortHost = p(1)
    val substrate = "host" // TODO fix when we start deploying to docker hosts.

    // See https://cwiki.dev.pages/display/ops/Graphite+-+Metrics+gathering#Graphite-Metricsgathering-Namespacingmetrics
    // v1.ENV.GROUP.REGION.HOSTNAME.SUBSTRATE
    s"v1.$env.$group.$region.$shortHost.$substrate"
  }

  def start(): Unit

  def stop(): Unit

  def close(): Unit

}
