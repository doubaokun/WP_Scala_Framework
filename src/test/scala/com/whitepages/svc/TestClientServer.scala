package com.whitepages.svc

/*
import com.whitepages.TestMain
import com.persist.JsonOps._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import com.whitepages.generated._
import com.whitepages.framework.client.Client
import org.scalatest.{Matchers, BeforeAndAfterAll, FunSpec}
import scala.language.postfixOps
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport

class TestClientServer extends FunSpec with BeforeAndAfterAll with Matchers with ClassSupport {
  private[this] val main: TestMain = new TestMain()
  private[this] var client: Client = null
  private[this] var info: ClientCallback.Info = null

  override def beforeAll {
    info = main.testBeforeSystem(TestMain.testConfig)
    client = Client(info.refFactory, "self")
  }

  override def afterAll {
    Await.result(client.stop, 1 minute)
    main.testAfter()
  }

  //
  // Check that we can back an expected response for the three protocols: JSON, Thrift
  // and JSON-in-query-string.
  //
  describe("valid JSON requests") {

    it("should respond correctly") {
      val request = JsonObject(
        "address1" -> "1301 5th ave",
        "address2" -> "1600",
        "location" -> "seattle, WA")
      val request1 = JsonObject("request" -> request)
      val f = client.postJson("testcmd", request1, noId)
      val response = Await.result(f, 10 seconds)
      assert(jget(response, "success", "zip") === "2625")
      assert(jget(response, "success", "county") == "King")
      log.debug(noId, response)
      // {m:{"A|3":"foo","B|0"->"bar"}}  cmd3
    }


    it("should respond correctly/map,union") {
      val request = JsonObject(
        "m" -> JsonObject(
          """{"c1":"A","c2":3}""" -> "foo",
          """{"c1":"B","c2":0}""" -> "bar"
        )
      )
      val request1 = JsonObject("in1" -> request)
      val f = client.postJson("testcmd3", request1, noId)
      val response = Await.result(f, 10 seconds)
      log.debug(noId, response)
      request should === (jget(response, "success"))
    }

    describe("case conversions") {

      describe("field names given in snake case in the thrift file") {
        it("should handle field name given in snake case in the JSON body") {
          val request = JsonObject("case_checker" -> JsonObject("a_name" -> 1))
          val f = client.postJson("casechecker", request, noId)
          val response = Await.result(f, 10 seconds)
          response should === (JsonObject("success" -> JsonObject("a_name" -> 1)))
          log.debug(noId, response)
        }
        // This is annoying to implement and we are not sure at this point that this is really necessary
        // Commenting out for now.
        // Please feel free to remove in a while if nothing has broken
//        it("should handle field name in camel case in the JSON body") {
//          val request = JsonObject("case_checker" -> JsonObject("aName" -> 1))
//          val f = client.postJson("casechecker", request, noId)
//          val response = Await.result(f, 10 seconds)
//          response should === (JsonObject("success" -> JsonObject("a_name" -> 1)))
//          log.debug(noId, response)
//        }
      }
      describe("method names") {
        describe("given in snake case in thrift file") {
          it("should handle method given in snake case in the JSON body") {
            val request = JsonObject("i" -> 42)
            val f = client.postJson("snake_case", request, noId)
            val response = Await.result(f, 10 seconds)
            log.debug(noId, Compact(response))
            assert(jget(response, "success") === 42)
            log.debug(noId, response)
          }
          it("should handle method given in camel case in the JSON body") {
            val request = JsonObject("i" -> 43)
            val f = client.postJson("snakeCase", request, noId)
            val response = Await.result(f, 10 seconds)
            assert(jget(response, "success") === 43)
            log.debug(noId, response)
          }
        }
        describe("given in camel case in thrift file") {
          it("should handle method given in snake case in the JSON body") {
            val request = JsonObject("i" -> 42)
            val f = client.postJson("camel_case", request, noId)
            val response = Await.result(f, 10 seconds)
            log.debug(noId, Compact(response))
            assert(jget(response, "success") === 42)
            log.debug(noId, response)
          }
          it("should handle method given in camel case in the JSON body") {
            val request = JsonObject("i" -> 43)
            val f = client.postJson("camelCase", request, noId)
            val response = Await.result(f, 10 seconds)
            assert(jget(response, "success") === 43)
            log.debug(noId, response)
          }
        }
      }
    }
  }

  describe("valid query string requests") {

    describe("case conversions") {

      describe("field names given in snake case in the thrift file") {
        it("should accept field name in snake case in the query string") {
          val f = client.getJson("casechecker", noId, JsonObject("case_checker" -> JsonObject("a_name" -> 1)))
          val json = Await.result(f, 10.seconds)
          jget(json, "success", "a_name") should equal(1)
        }
        // This is annoying to implement and we are not sure at this point that this is really necessary
        // Commenting out for now.
        // Please feel free to remove in a while if nothing has broken
//        it("should accept field name in camel case") {
//          val f = client.getJson("casechecker", noId, JsonObject("case_checker" -> JsonObject("aName" -> 1)))
//          val json = Await.result(f, 10.seconds)
//          json should === (JsonObject("success" -> JsonObject("a_name" -> 1)))
//        }
      }
      describe("given in snake case in thrift file") {
        it("should accept method name in snake case in the query string") {
          val f = client.getJson("snake_case", noId, JsonObject("i" -> 42))
          val json = Await.result(f, 10.seconds)
          jget(json, "success") should equal(42)
        }
        it("should accept method name in camel case in the query string") {
          val f = client.getJson("snakeCase", noId, JsonObject("i" -> 43))
          val json = Await.result(f, 10.seconds)
          jget(json, "success") should equal(43)
        }
      }
      describe("given in camel case in thrift file") {
        it("should accept method name in snake case in the query string") {
          val f = client.getJson("camel_case", noId, JsonObject("i" -> 42))
          val json = Await.result(f, 10.seconds)
          jget(json, "success") should equal(42)
        }
        it("should accept method name in camel case in the query string") {
          val f = client.getJson("camelCase", noId, JsonObject("i" -> 43))
          val json = Await.result(f, 10.seconds)
          jget(json, "success") should equal(43)
        }
      }
    }
  }

  describe("valid Thrift request") {
    describe("case conversion") {
      describe("case conversion") {
        describe("method name given in snake case in thrift file") {
          it("should respond correctly") {
            val request = Test.snakeCase$args(-42)
            val f: Future[Test.snakeCase$result] = client.postThrift[Test.snakeCase$args, Test.snakeCase$result]("snake_case", request, noId)
            val response = Await.result(f, 10 seconds)
            response.success should === (Some(-42))
            log.debug(noId, "THRIFT=" + response)
          }
        }
        describe("method name given in camel case in thrift file") {
          it("should respond correctly") {
            val request = Test.camelCase$args(-43)
            val f = client.postThrift[Test.camelCase$args, Test.camelCase$result]("camelCase", request, noId)
            val response = Await.result(f, 10 seconds).asInstanceOf[Test.camelCase$result]
            response.success match {
              case Some(r) =>
                assert(r === -43)
              case None => fail("thrift result None")
            }
            log.debug(noId, "THRIFT=" + response)
          }
        }
      }
    }

    //
    // Check we get back the correct object structure if the application
    // code returns a server exception (i.e. returns an exception object,
    // not throwing a code exception).
    //

    describe("when application code returns an exception object") {
      describe("when accessed as thrift") {
        it("should respond with a thrift object containing a ServerException") {
          // In TestActor, the shortIdToUuid returns a ServerException if given ids=bad
          val request = Test.shortIdToUuid$args("bad")
          val f = client.postThrift[Test.shortIdToUuid$args, Test.shortIdToUuid$result]("shortIdToUuid", request, noId)
          val response = Await.result(f, 10.seconds).asInstanceOf[Test.shortIdToUuid$result]
          response.success match {
            case None => // expected result
            case Some(r) => fail("Expected exception, got success")
          }
          response.e match {
            case None => fail("Exception exception, got no exception")
            case Some(r) =>
              r.message.get should include("simulated error")
          }
        }
      }
    }
  }

}
*/


