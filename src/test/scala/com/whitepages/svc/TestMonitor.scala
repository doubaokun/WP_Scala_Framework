package com.whitepages.svc

import com.codahale.metrics.MetricRegistry

object TestMonitor {
  def ext(metrics: MetricRegistry): PartialFunction[Any, Unit] = {
    val testCounter = metrics.counter("test.testcmd")

    PartialFunction[Any,Unit] {
      case ("test", "testcmd") => // TODO: refactor to typed messages
        testCounter.inc(2)
    }
    // TODO: verify behavior after reset
  }
}
