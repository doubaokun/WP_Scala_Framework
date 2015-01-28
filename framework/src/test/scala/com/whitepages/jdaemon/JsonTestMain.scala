package com.whitepages.jdaemon

import com.whitepages.svc.TestMonitor
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import com.whitepages.framework.service.JsonService

class JsonTestMain extends JsonService {

  val serviceName = "scala-webservice"
  //val thriftPath = "com.whitepages.generated"
  //val thriftName = "Test"
  val handlerFactory = JTestHandlerFactory
  val monitorExt:Option[(MetricRegistry) => PartialFunction[Any, Unit]] = Some(TestMonitor.ext)
  val queryStringHandler = None
}

object JsonTestMain {
  val testConfig = Some(ConfigFactory.parseResources("test.conf"))

  def main(args: Array[String]) {
    new JsonTestMain().runServer(testConfig)
  }
}
