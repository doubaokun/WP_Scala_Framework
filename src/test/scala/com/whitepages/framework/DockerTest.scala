package com.whitepages.framework

import com.whitepages.framework.service.DockerRunner

object DockerTest {

  def main(args:Array[String]) {
    //val args = Array[String]("com.whitepages.jdaemon.JsonTestMain", "10.0.1.6", "all,qa59,qa")
    val args = Array[String]("com.whitepages.jdaemon.JsonTestMain", "172.26.15.28", "all,qa59,qa")
    DockerRunner.enableSignals = true
    DockerRunner.main(args)
  }

}
