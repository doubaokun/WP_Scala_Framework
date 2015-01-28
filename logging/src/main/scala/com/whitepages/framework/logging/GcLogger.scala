package com.whitepages.framework.logging

import javax.management.{Notification, NotificationListener, NotificationEmitter}
import scala.collection.JavaConversions._
import com.sun.management.GarbageCollectionNotificationInfo
import javax.management.openmbean.CompositeData
import com.persist.JsonOps._
import java.lang.management.MemoryUsage

// Code based on http://www.fasterj.com/articles/gcnotifs.shtml

trait GcLogAct {
  def send(j: JsonObject)
}

object DefaultGcLogAct extends GcLogAct with ClassLogging {
  def send(data: JsonObject) {
    log.alternative("gc", data)
  }
}

private[logging] case class GcLogger(logAct:GcLogAct) {

    // get all the GarbageCollectorMXBeans - there's one for each heap generation
    // so probably two - the old generation and young generation
    private[this] val gcbeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
    // Install a notification handler for each bean
    private[this] val emitters = for (gcbean <- gcbeans) yield {
      val emitter = gcbean.asInstanceOf[NotificationEmitter]
      //use an anonymously generated listener for this example
      // - proper code should really use a named class
      val listener = new NotificationListener() {

        // implement the notifier callback handler
        @Override
        def handleNotification(notification: Notification, handback: Object) {
          // we only handle GARBAGE_COLLECTION_NOTIFICATION notifications here
          if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            // get the information associated with this notification
            val info = GarbageCollectionNotificationInfo.from(notification.getUserData().asInstanceOf[CompositeData])
            // get all the info and log it

            // There is some indication from the documentation that getDuration returns milliseconds.
            // http://docs.oracle.com/javase/7/docs/jre/api/management/extension/com/sun/management/GcInfo.html#getDuration()
            // However!!!! emperical data indicates otherwise as does commentary in the original example.
            val duration = info.getGcInfo().getDuration() // microseconds

            var gcaction = info.getGcAction()
            val gctype = if ("end of minor GC".equals(gcaction)) {
              "minor"
            } else if ("end of major GC".equals(gcaction)) {
              "major"
            } else {
              "unknown"
            }

            def getMem(mem: Map[String, MemoryUsage]): Json = {
              val m = mem map {
                case (name, usage) =>
                  (name, JsonObject("used" -> usage.getUsed, "max" -> usage.getMax, "committed" -> usage.getCommitted))
              }
              m
            }

            val data = JsonObject("type" -> gctype, "id" -> info.getGcInfo().getId(), "name" -> info.getGcName(), "cause" -> info.getGcCause(),
              "start" -> info.getGcInfo().getStartTime, "end" -> info.getGcInfo.getEndTime,
              "before" -> getMem(info.getGcInfo.getMemoryUsageBeforeGc.toMap[String, MemoryUsage]),
              "after" -> getMem(info.getGcInfo.getMemoryUsageAfterGc.toMap[String, MemoryUsage]),
              "duration" -> duration)


            if (!LoggingState.loggerStopping) logAct.send(data)
          }
        }
      }

      //Add the listener
      emitter.addNotificationListener(listener, null, null)
      (emitter,listener)
  }

  def stop: Unit = {
    for ((e,l)<-emitters) {
      e.removeNotificationListener(l)
    }
  }
}