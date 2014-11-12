package com.whitepages.svc

import scala.concurrent.{Promise, Future}
import akka.actor._
import scala.concurrent.duration._
import akka.util.Timeout
import com.whitepages.generated._
import scala.Some
import com.whitepages.framework.service.ThriftService
import com.whitepages.framework.exceptions.BadInputException
import com.whitepages.framework.logging.{noId, RequestId}
import com.whitepages.framework.util.ClassSupport
import scala.language.postfixOps
import com.persist.JsonOps._


class TestHandlerThrift(actorFactory: ActorRefFactory) extends ThriftService.Handler with ClassSupport {
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] implicit val ec = actorFactory.dispatcher

  def act(hr: ThriftService.Request): Future[ThriftService.Response] = {
    val f = (hr.cmd, hr.request, hr.requestId) match {

      case ("testcmd", request: Test.testcmd$args, id: RequestId) =>
        Time(id, "testcmd:trace") {
          log.trace(id, "Got a testcmd message")
        }

        Future {
          Time(id, "testcmd:response") {
            val r = Response("2625", "King")
            val rproxy = Test.testcmd$result(Some(r))
            monitor !("test", "testcmd")
            rproxy
          }
        }

      case ("testcmd1", request: Test.testcmd1$args, id: RequestId) =>
        // testcmd1 is used to test what happens when a handler in an application throws
        // an exception

        //log.debug(id, "Got a testcmd1 message")

        Future {
          log.error(id, "testcmd1 NYI")
          throw new Exception("testcmd1 NYI")
        }

      case ("testcmd3", request: Test.testcmd3$args, id: RequestId) =>
        Future {
          val r = Response("2625", "King")
          val rproxy = Test.testcmd3$result(Some(request.in1))
          monitor !("test", "testcmd")
          rproxy
        }

      case ("shortIdToUuid", request: Test.shortIdToUuid$args, id: RequestId) =>
        // shortIdToUuuid is used to test pattern when result could be a success or an exception
        // if request.ids is the word "bad" respond with an exception
        // if request.ids is anything else, respond with a success

        Future {
          val result: Test.shortIdToUuid$result = if (request.ids == "bad") {
            Test.shortIdToUuid$result(e = Some(ServerException(message = Some(s"shortIdToUuid simulated error for input ${request.ids}"))))
          } else {
            Test.shortIdToUuid$result(success = Some(s"uuid_for_${request.ids}"))
          }
          result
        }

      case ("casechecker", request: Test.casechecker$args, id: RequestId) =>
        //log.debug(id, "Got a caseChecker message")
        Future {
          //log.debug(id, s"Responding with input: ${request.caseChecker}")
          Test.casechecker$result(success = Some(request.caseChecker))
          //throw new com.whitepages.framework.exceptions.BadInputException(JsonObject("foo"->3))
        }

      case ("snakeCase", request: Test.snakeCase$args, id: RequestId) =>
        //log.debug(id, "Got a snakeCase message")
        Future {
          //log.debug(id, s"Responding with input: ${request.i}")
          Test.snakeCase$result(success = Some(request.i))
        }

      case ("camelCase", request: Test.camelCase$args, id: RequestId) =>
        //log.debug(id, "Got a camelCase message")
        Future {
          //log.debug(id, s"Responding with input: ${request.i}")
          Test.camelCase$result(success = Some(request.i))
        }

      case (cmd, request: Any, id: RequestId) =>
        //log.debug(id, s"Got a ${cmd} message")

        Future {
          throw new BadInputException("Unrecognized command: " + cmd)
        }

      case (x, _, id: RequestId) =>
        Future {
          log.error(id, "Bad command: " + x)
          throw new BadInputException("Bad command: " + x)
        }

      case x =>
        Future {
          log.error(noId, "Bad message: " + x)
          throw new BadInputException("Bad message: " + x)
        }
    }

    f map { case x => ThriftService.Response(x)}
  }

  override def warmup(percent: (Int) => Unit) = {
    log.info(noId, "warming up")
    Thread.sleep(2000)
    Future.successful[Unit](())
  }

}

object TestHandlerFactoryThrift extends ThriftService.HandlerFactory {

  def start(actorFactory: ActorRefFactory) = new TestHandlerThrift(actorFactory)

}
