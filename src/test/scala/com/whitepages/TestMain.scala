package com.whitepages

import com.whitepages.svc.{TestMonitor, TestHandlerFactoryThrift}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import com.whitepages.framework.service.ThriftService

class TestMain extends ThriftService {

  val serviceName = "scala-webservice"
  val thriftPath = "com.whitepages.generated"
  val thriftName = "Test"
  val handlerFactory = TestHandlerFactoryThrift
  val monitorExt:Option[(MetricRegistry) => PartialFunction[Any, Unit]] = Some(TestMonitor.ext)
  val queryStringHandler = None
}

object TestMain {
  val testConfig = Some(ConfigFactory.parseResources("test.conf"))
  val balancerConfig = Some(ConfigFactory.parseResources("balancer.conf"))

  def main(args: Array[String]) {
    new TestMain().runServer(testConfig)
  }
}
