package com.whitepages.framework.logging

import com.persist.JsonOps._
import scala.language.existentials

private[logging] object LogActorMessages {
  // Messages for Log Actor

  private[logging] trait LogActorMessage

  private[logging] case class LogMessage(level: Int, id: AnyId,
                                         time: Long, className: Option[String], actorName: Option[String], msg: Json,
                                         line: Int, file: String, ex: Throwable, kind: String = "") extends LogActorMessage

  private[logging] case class AltMessage(category: String, time: Long, j: JsonObject) extends LogActorMessage

  private[logging] case class AkkaMessage(time: Long, level: Int, source: String, clazz: Class[_], msg: Any, cause: Option[Throwable])
    extends LogActorMessage

  private[logging] case object LastAkkaMessage extends LogActorMessage

  private[logging] case object StopLogging extends LogActorMessage

}
