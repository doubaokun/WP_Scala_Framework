package com.whitepages.framework.logging

import com.whitepages.framework.util.ActorSupport
import scala.collection.mutable
import com.persist.JsonOps._

private[framework] class TimeActor extends ActorSupport {

  private case class TimeStep(name: String, start: Long, var end: Long = 0)

  private case class TimeItem(start: Long, steps: mutable.HashMap[String, TimeStep] = mutable.HashMap[String, TimeStep]())

  private val items = mutable.HashMap[String, TimeItem]()

  def start(id: RequestId, time: Long) {
    val key = s"${id.trackingId}\t${id.spanId}"
    items += (key -> TimeItem(time))
  }

  def end(id: RequestId) {
    val key = s"${id.trackingId}\t${id.spanId}"
    items.get(key) map {
      case timeItem =>
        val jitems0 = timeItem.steps map {
          case (key1, timeStep) =>
            val j1 = JsonObject("A name" -> timeStep.name, "B start" -> timeStep.start)
            val j2 = if (timeStep.end == 0) {
              emptyJsonObject
            } else {
              JsonObject("C end" -> timeStep.end, "D duration" -> DurationStrings.microsDuration(timeStep.end - timeStep.start))
            }
            j1 ++ j2
        }
        val traceId = JsonArray(id.trackingId, id.spanId)
        val jitems = jitems0.toSeq.sortBy(jgetInt(_, "B start"))
        val j = JsonObject("@traceId" -> traceId, "items" -> jitems)
        log.alternative("time", j)
        items -= key
    }
  }

  def logStart(id: RequestId, name: String, uid: String, time: Long) {
    val key = s"${id.trackingId}\t${id.spanId}"
    val key1 = s"${name}\t${uid}"
    items.get(key) map {
      case timeItem => timeItem.steps += (key1 -> TimeStep(name, time - timeItem.start))
    }
  }

  def logEnd(id: RequestId, name: String, uid: String, time: Long) {
    val key = s"${id.trackingId}\t${id.spanId}"
    val key1 = s"${name}\t${uid}"
    items.get(key) map {
      case timeItem =>
        timeItem.steps.get(key1) map {
          case timeStep =>
            timeStep.end = time - timeItem.start
        }
    }
  }

  def receive = {
    case LoggingControl.TimeStart(id, name, uid, time) =>
      if (name == "") start(id, time)
      logStart(id, name, uid, time)

    case LoggingControl.TimeEnd(id, name, uid, time) =>
      logEnd(id, name, uid, time)
      if (name == "") end(id)

    case _ =>
  }

}

