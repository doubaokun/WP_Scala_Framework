package com.whitepages.framework.client

import com.persist.JsonOps._
import scala.concurrent.Await
import scala.concurrent.duration._
import com.whitepages.TestMain
import org.scalatest._
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport
import scala.language.postfixOps

/*
class TestSOLR extends FunSpec with Matchers with ClassSupport with SOLRTestData with BeforeAndAfterAll {
  private[this] val main = new TestMain()
  private[this] var info: ClientCallback.Info = null
  private[this] var client: SOLRClient = null

  override def beforeAll {
     info = main.testBefore(TestMain.testConfig)
    client = SOLRClient(info.refFactory, "SOLR")
  }

  override def afterAll {
    Await.result(client.stop, 1 minute)
    main.testAfter()
  }

  describe("SOLR") {
    it("SOLR Test") {
      val j = Json(data)
      val args = jgetObject(j, "responseHeader", "params")
      val responseFuture = client.callSOLR(args, noId)
      Await.result(responseFuture, 1 minute) match {
        case j: Json =>
          log.debug(noId, j)
          val docs = jgetArray(j, "response", "docs")
          val listing_id = "377c23b5-962b-4d20-bea7-0858460e6584"
          val matches = docs.filter(listing_id == jgetString(_, "listing_id").split( """\|""")(1))
          matches.size shouldBe 1
      }
      // TODO print monitor stuff??
    }
  }
}
*/
