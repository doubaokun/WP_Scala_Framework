package com.whitepages.svc
/*

import com.whitepages.TestMain
import com.persist.JsonOps._
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.matchers.ShouldMatchers
import com.whitepages.framework.client.Client
import org.scalatest.{FunSpec}
import scala.language.postfixOps
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport

class TestBalancer extends FunSpec {

  object Callback extends ClientCallback {
    def act(info: ClientCallback.Info) = {
      val client = Client(info.refFactory, "scala-webservice")
      val ok = TestAll.test(new TestBalancerGroup(client, info))
      Await.result(client.stop, 1 minute)
      assert(ok, "Group Failed")
    }
    it("TestBalancer") {
      //new TestMain().runClientServer(Callback, TestMain.balancerConfig)
    }
  }
}

private class TestBalancerGroup(client: Client, info: ClientCallback.Info) extends FunSpec
with ShouldMatchers with ClassSupport {

  describe("Test Balancer") {

    it("should respond correctly") {
      implicit val ec: ExecutionContext = info.refFactory.dispatcher
      val request = JsonObject(
        "address1" -> "1301 5th ave",
        "address2" -> "1600",
        "location" -> "seattle, WA")
      val request1 = JsonObject("request" -> request)
      val fs = for (i <- 1 to 3) yield {
        val f = client.postJson("testcmd", request1, noId)
        f map {
          case response =>
            assert(jget(response, "success", "zip") === "2625")
            assert(jget(response, "success", "county") == "King")
            log.info(noId, JsonObject("i"->i, "response"->response))
        }
        f recover {
          case ex: Throwable => log.error(noId, "Send failed: " + ex.toString)
        }
        Await.result(f, 10 seconds)
        Thread.sleep(1000)
        f
      }
      try {
        Await.result(Future.sequence(fs), 60 seconds)
      } catch {
        case ex: Throwable => log.error(noId, "Balancer test fail: " + ex.toString)
      }
    }
  }
}
*/


