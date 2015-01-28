package com.whitepages.framework.client.balancer

import com.persist.JsonOps._
import com.whitepages.framework.client.{Balancer, DriverMessages}
import akka.actor.{ActorRefFactory, Props}
import scala.concurrent.duration._
import scala.language.postfixOps
import com.whitepages.framework.logging.{RequestId, noId}
import scala.concurrent.Await
import com.whitepages.jdaemon.JsonTestMain
import com.typesafe.config.ConfigFactory
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.client.balancer.TestDriver._

class TestBalancer(actorFactory: ActorRefFactory) extends ClassSupport {
  private[this] val clientName = "test-client"
  //private[this] val connections = 2
  //private[this] val retryInterval = 10 minutes

  private[this] val plan1 = Seq[PlanEvent](PlanEvent(Normal, 0 seconds))
  private[this] val plan2 = Seq[PlanEvent](PlanEvent(Zombie, 0 seconds))
  private[this] val plan3 = Seq[PlanEvent](
    PlanEvent(Normal, 0 seconds),
    PlanEvent(Zombie, 20 seconds),
    PlanEvent(Normal, 80 seconds)
  )

  private[this] var successCnt = 0
  private[this] var failCnt = 0

  private def test1(pos: Int) {
    val id = RequestId()
    try {
      val result = Await.result(balancer.call(JsonObject("msg" -> "test", "pos" -> pos), id, 100), 1 minute)
      log.info(id, result)
      successCnt += 1
    } catch {
      case ex: Throwable =>
        log.warn(id, "fail", ex)
        failCnt += 1
    }
  }

  private def testMinute {
    val t = System.currentTimeMillis()
    val t1 = t + 120 * (1000)
    var pos = 0
    while (System.currentTimeMillis() < t1) {
      pos += 1
      test1(pos)
      Thread.sleep(10000)
    }
  }

  val fixPlan = fix(plan3)

  private def driverProps(driverMessages: DriverMessages[Json, Json], host: String, port: Int) = {
    Props(classOf[TestDriver], driverMessages, host, port, clientName, JsonObject(), fixPlan)
  }

  private[this] val balancer = Balancer[Json, Json](driverProps, actorFactory, clientName)
  //test1(1)
  testMinute
  Await.result(balancer.stop, 30 seconds)
  log.info(noId, JsonObject("msg" -> "COUNTS", "ok" -> successCnt, "fail" -> failCnt))
}

object TestBalancer {
  def main(args: Array[String]) {
    object Callback extends ClientCallback {
      def act(info: ClientCallback.Info) = {
        new TestBalancer(info.refFactory)
      }
    }
    val testConfig = Some(ConfigFactory.parseResources("test.conf"))
    val main = new JsonTestMain()
    main.runClient(Callback, testConfig)
  }
}
