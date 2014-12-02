package com.whitepages.jdaemon

import com.persist.JsonOps._
import scala.concurrent.Await
import scala.concurrent.duration._
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport
import scala.language.postfixOps
import com.whitepages.framework.client.Client
import org.scalatest.{BeforeAndAfterAll, Matchers, FunSpec}
import com.whitepages.framework.service.ClientCallback

class JTestClientServer extends FunSpec with Matchers with ClassSupport with BeforeAndAfterAll {
  private[this] val main = new JsonTestMain()
  private[this] var info: ClientCallback.Info = null
  private[this] var client: Client = null

  override def beforeAll {
    info = main.testBeforeSystem(JsonTestMain.testConfig)
    client = Client(info.refFactory, "self")
  }

  override def afterAll {
    Await.result(client.stop, 1 minute)
    main.testAfter()
  }

  describe("valid JSON requests") {

    it("should respond correctly") {
      val request = JsonObject(
        "address1" -> "1301 5th ave",
        "address2" -> "1600",

        "location" -> "seattle, WA")
      val f = client.postJson("echo", request, noId)
      val response = Await.result(f, 10 seconds)
      assert(response === request)
    }
  }
}



