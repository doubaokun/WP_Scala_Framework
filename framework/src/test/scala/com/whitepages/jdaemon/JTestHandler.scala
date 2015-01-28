package com.whitepages.jdaemon

import scala.concurrent.{Promise, Future}
import akka.actor._
import scala.concurrent.duration._
import akka.util.Timeout
import com.whitepages.framework.service.JsonService._
import com.whitepages.framework.exceptions.BadInputException
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport
import scala.language.postfixOps


class JTestHandler(factory: ActorRefFactory) extends Handler with ClassSupport {
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] implicit val ec = factory.dispatcher

  //monitor ! "foo"

  def act(hr: Request): Future[Response] = {
    //val p = Promise[Json]
    val f = hr.cmd match {
      case "echo" =>
        Future {
          hr.request
        }
      case x:String =>
        Future {
          log.error(hr.requestId, "Bad command: " + x)
          throw new BadInputException("Bad command: " + x)
        }

      case x =>
        Future {
          log.error(noId, "Bad message: " + x)
          throw new BadInputException("Bad message: " + x)
        }
    }
    f map {
      case result => Response(result)
    }
  }

}

object JTestHandlerFactory extends HandlerFactory {

  def start(factory: ActorRefFactory) = new JTestHandler(factory)

}
