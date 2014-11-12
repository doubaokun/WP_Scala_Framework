package com.whitepages.framework.client

import scala.concurrent.duration._
import com.whitepages.TestMain
import org.scalatest._
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKitBase, TestActorRef, ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import scala.language.postfixOps
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.logging.{noId, RequestId}
import com.whitepages.framework.util.ClassSupport
import com.whitepages.framework.client.driverIds.{HealthId, ReqTryId}
import com.whitepages.framework.client.RedisClient._
import java.net.{InetAddress, Socket}

/*
class TestRedisActor extends FunSpec {

  object Callback extends ClientCallback with ClassSupport {

    def act(info: ClientCallback.Info) {
      if (checkForRedis()) {
        val config = system.settings.config
        val ok = TestAll.test(new TestRedisActorGroup(info, config))
        assert(ok, "Group failed")
      } else {
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

  it("Test Redis Actor") {
    val testConfig = Some(ConfigFactory.parseResources("sample-svc-test.conf"))
    new TestMain().runClientServer(Callback, TestMain.testConfig)
  }

}

//http://www.superloopy.io/articles/2013/scalatest-with-akka.html
abstract class TestKitSpec2(name: String, config: Config)
  //extends TestKit(ActorSystem(name, config))
  //with FunSpec
  extends FunSpec with TestKitBase
  //with MustMatchers
  with ShouldMatchers
  with BeforeAndAfterAll
  with ImplicitSender
  with BeforeAndAfterEach {
}

private class TestRedisActorGroup(info: ClientCallback.Info, config: Config) extends TestKitSpec2("test-redis-socket-system", config) {

  implicit val system:ActorSystem = ActorSystem("test-redis-socket-system", config)

  //  class TestRedisActor extends TestKitSpec2("test-redis-socket-system", "test.conf") {

  val runSingletons = true
  val runXxTimes    = 100

  var connected = false

  val driverMessages = new DriverMessages[RedisRequest, RedisResponse]
  type DriverReceive = driverMessages.DriverReceive

  val healthRequest = RedisHealthCheck
  // TODO fix
  //new RedisRequest("123", "com_check", "<DQ_ComCheck/>")
  //  val serviceName   = "scala-webservice"
  val callTimeout   = 2.seconds
  val clientName    = "att"
  val host          = "localhost"
  val port          = 6379

  val redisActor = TestActorRef(Props(classOf[RedisActor], driverMessages, clientName, healthRequest, callTimeout, host, port, None, None), supervisor = testActor, "redis-actor")

  expectMsgPF() {
    case driverMessages.DriverReady =>
      connected = true
      assert(true, "Received DriverReady")
    case x: driverMessages.DriverConnectFailed =>
      assert(true, s"Unable to connect to Redis, ignoring Redis tests: $x")
    case x: Any => assert(false, s"Received unexpected Msg: $x")
  }

  //  val redis      = redisActor.underlyingActor.asInstanceOf[RedisActor]
  type FixtureParam = ClientCallback.Info


  def gotCorrectData(expected: RedisDataType, actual: RedisDataType): Boolean = {
    val dataGotType = actual match {
      case x: expected.type => true
      case _ => false
    }

    val dataGotData = actual match {
      case x: RedisDataKeyValue => (x.key == expected.key) && (expected.asInstanceOf[x.type].value == x.value)
      case _ => false
    }
    dataGotType && dataGotData
  }

  describe("Redis") {
    if (connected == true) {
      if (runSingletons) {

        it("Redis Query") {
          val key = (Math.random() * 1000).toLong.toString
          val redisSet = RedisSet(noId, RedisCmdSet, RedisDataKeyValue(key, "Bar"))
          val redisGet = RedisGet(noId, RedisCmdGet, RedisDataKey(key))
          val redisDel = RedisSet(noId, RedisCmdDel, RedisDataKey(key))
          var id = 10000

          redisActor ! driverMessages.DriverSend(redisSet, RequestId(id.toString, "Baz"), ReqTryId(id, 1))
          fishForMessage(6 seconds) {
            case x: DriverReceive => true
            case x =>
              //                println(s"Received Msg: $x")
              false
          }

          id += 1
          redisActor ! driverMessages.DriverSend(redisGet, RequestId(id.toString, "Bif"), ReqTryId(id, 1))
          try {
            fishForMessage(6 seconds) {
              case x: DriverReceive =>
                //                  println(s"Redis query: $x")
                val r = x.out
                val gotData = r match {
                  case y: RedisResponseData => gotCorrectData(RedisDataKeyValue(key, "Bar"), y.data)
                  case _ => false
                }
                r.isInstanceOf[RedisResponse] && gotData
              case x@driverMessages.DriverAck =>
                //                  println(s"ACK: $x")
                false
              case x => println(s"Test Received: $x"); false
            }
          }
          catch {
            case e: Throwable =>
              id += 1
              redisActor ! driverMessages.DriverSend(redisDel, RequestId(id.toString, "Qu"), ReqTryId(id, 1))
              throw e
          }
          id += 1
          redisActor ! driverMessages.DriverSend(redisDel, RequestId(id.toString, "Qua"), ReqTryId(id, 1))

        }

        it("Redis HealthCheck") {
          redisActor ! driverMessages.DriverHealthCheck(HealthId(10000))
          fishForMessage(6 seconds) {
            case x: driverMessages.DriverHealthResult =>
              true
            case x@driverMessages.DriverAck =>
              false
            case x =>
              false
          }
        }

        //        it("Should fail fast when using a bad IP address") {
        //          fixture =>
        //            Thread.sleep(2000)
        //            badRedisActor ! driverMessages.DriverHealthCheck(HealthId(10000))
        //            fishForMessage(6 seconds) {
        //              case x: driverMessages.DriverHealthResult => println(s"HEALTH: $x"); true
        //              case x@driverMessages.DriverAck => println(s"ACK: $x"); false
        //              case x => println(s"Test Received: $x"); false
        //            }
        //        }
      }
    }
    else {
      it("Is Unable to Connect") {
        assert(true)
      }
    }
  }
}
*/
