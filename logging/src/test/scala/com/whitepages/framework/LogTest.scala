package com.whitepages.framework

import java.io.{FileWriter, File}

import akka.actor.{PoisonPill, Actor, ActorSystem, Props}
import com.persist.JsonOps._
import com.typesafe.config.{ConfigFactory, Config}
import com.whitepages.framework.exceptions.FrameworkException
import com.whitepages.framework.logging._
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps

case class TestException(message: Json) extends FrameworkException(message)

class TestActor() extends Actor with akka.actor.ActorLogging {
  log.warning("akka log test")

  def receive = {
    case "foo" => log.error("foo")
    case x: Any =>
  }
}

case class LogTest(system: ActorSystem) extends ClassLogging {
  log.info(noId, "test")
  log.warn(RequestId(), JsonObject("a" -> 1, "b" -> "abc"))
  log.error(noId, "exception1", new Exception("exception1"))
  log.error(noId, "exception2", TestException(JsonObject("kind" -> "fail", "value" -> 23)))
  log.alternative("extra", JsonObject("a" -> false, "b" -> "c"))

  val slf4jlog = LoggerFactory.getLogger(classOf[LogTest])
  slf4jlog.error("slf4j")
}

case class TimeTest(system:ActorSystem) extends Timing {
  private[this] implicit val ec:ExecutionContext = system.dispatcher

  def test(id: RequestId): Future[Int] = {
    time.start(id, name = "")
    val f1 = Future {
      val v1 = Time(id, name = "step1") {
        23 + 17

      }
      Time(id, name = "step2") {
        v1 * 2
      }
    }
    f1 map {
      case v =>
        time.end(id, name = "")
        v + 1
    }
  }
}

object LogTest extends ClassLogging {

  def main(args: Array[String]) {

    val config = ConfigFactory.load("test.conf")
    val loggingConfig = config.getConfig("wp.logging")

    val system = ActorSystem("test", config)

    val loggingSystem = LoggingSystem(system, loggingConfig, "test", "0.0.1", "myhost", true)
    val test = LogTest(system)
    val testActor = system.actorOf(Props(classOf[TestActor]), name = "testActor")
    testActor ! "foo"
    testActor ! PoisonPill

    val id = RequestId()
    val f = TimeTest(system).test(id)
    log.info(id, JsonObject("timeResult"->Await.result(f, 30 seconds)))

    // following to test gc log
    (1 until 10000).map {
      case i => "foo" + i
    }

    Await.result(loggingSystem.stop, 30 seconds)
    system.shutdown()
  }

}
