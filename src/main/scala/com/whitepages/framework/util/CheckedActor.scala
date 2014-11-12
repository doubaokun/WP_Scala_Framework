package com.whitepages.framework.util

import akka.actor.Actor
import scala.util.control.Exception
import com.whitepages.framework.logging.noId
import com.persist.JsonOps._

abstract class CheckedActor extends Actor with ActorSupport {
  def rec: PartialFunction[Any, Unit]

  def receive: PartialFunction[Any, Unit] = {
    case msg => {
      Exception.catching(classOf[Throwable])
        .withApply(
          t => log.error(noId, JsonObject("msg" -> "unhandled exception", "message" -> msg.toString, "from" -> sender.path.toString, "ex" -> t.toString), t)
        ) {
          val body1: PartialFunction[Any, Unit] = rec.orElse {
            case _ => {
              //log.error(noId, s"Unmatched message msg:$msg from:$sender to:$self")
              log.error(noId,JsonObject("msg"->"unmatched message", "from"->sender.path.toString, "to"->self.path.toString, "message"->msg.toString))
            }
          }
          body1(msg)
        }
    }
  }
}
