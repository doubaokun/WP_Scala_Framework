package com.whitepages.framework.client

import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._
import org.scalatest.matchers.ShouldMatchers
import com.whitepages.TestMain
import org.scalatest.{FunSpec}
import com.whitepages.framework.logging.{RequestId, noId}
import com.whitepages.framework.util.{ClassSupport}
import scala.language.postfixOps
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.client.RedisClient._
import com.whitepages.framework.exceptions.NotAvailableException
import java.net._

/*
class TestRedisClient extends FunSpec {

  object Callback extends ClientCallback {
    def act(info: ClientCallback.Info) = {
      val clientName = "redis"
      if (checkForRedis()) {
        val client = new RedisClient(info.refFactory, clientName, 2.seconds, Some(7))
        val ok = TestAll.test(new TestRedisGroup(client, info))
        client.stop
        assert(ok, "Group Failed")
      }
      else {
        assert(true, "Redis is not Installed")
      }
    }

    // This does a lightweight check to see if we even have a chance of connecting to a redis.
    def checkForRedis(): Boolean = {
      try {
        new Socket(InetAddress.getByName("localhost"), 6379)
        true
      }
      catch {
        case x: Throwable => false
      }
    }
  }

  it("TestRedisClient") {
    new TestMain().runClientServer(Callback, TestMain.testConfig)
  }
}

private class TestRedisGroup(client: RedisClient, info: ClientCallback.Info)
  extends FunSpec
  with ShouldMatchers
  with ClassSupport {


  describe("Test Redis") {

    it("should respond correctly") {
      implicit val ec: ExecutionContext = info.refFactory.dispatcher
      val key = (Math.random() * 1000).toLong.toString
      val redisSet = RedisSet(noId, RedisCmdSet, RedisDataKeyValue(key, "Bar"))
      val redisGet = RedisGet(noId, RedisCmdGet, RedisDataKey(key))
      val redisDel = RedisSet(noId, RedisCmdDel, RedisDataKey(key))
      val requestId = RequestId()


      try {
        val f = client.call(redisSet, requestId)
        f map {
          case RedisResponseData(x, y) =>
            //          log.info(noId, s"Success-----> $x :: $y")
            assert(y.key === key)
          case x => assert(false, s"Fail ------> $x")
        }
        f recover {
          case ex: NotAvailableException =>
            log.error(noId, "Send failed: " + ex.toString)
        }
        Await.result(f, 10 seconds)
        client.call(redisDel, requestId)
      }
      catch {
        case ex: NotAvailableException =>
          assert(true, s"Passing the test when Redis Is Not Installed: $ex")
      }
    }
  }
}
*/
