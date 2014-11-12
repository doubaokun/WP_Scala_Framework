package com.whitepages.framework.monitor

import javax.management.{Notification, NotificationListener, NotificationEmitter}
import scala.collection.JavaConversions._
import com.sun.management.GarbageCollectionNotificationInfo
import javax.management.openmbean.CompositeData
import akka.actor.ActorRef
import com.whitepages.framework.util.ClassSupport
import com.persist.JsonOps._
import java.lang.management.MemoryUsage
import com.whitepages.framework.logging.LoggingControl

// Code based on http://www.fasterj.com/articles/gcnotifs.shtml

private[monitor] object MonitorGC extends ClassSupport {
  def addGCMonitor(monitor: ActorRef) {
    val logOld = config.getBoolean("wp.logging.logOldGC")
    val logNew = config.getBoolean("wp.logging.logNewGC")
    //get all the GarbageCollectorMXBeans - there's one for each heap generation
    //so probably two - the old generation and young generation
    val gcbeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
    //Install a notification handler for each bean
    for (gcbean <- gcbeans) {
      val emitter = gcbean.asInstanceOf[NotificationEmitter]
      //use an anonymously generated listener for this example
      // - proper code should really use a named class
      val listener = new NotificationListener() {
        //keep a count of the total time spent in GCs
        // var totalGcDuration = 0L

        //implement the notifier callback handler
        @Override
        def handleNotification(notification: Notification, handback: Object) {
          //we only handle GARBAGE_COLLECTION_NOTIFICATION notifications here
          if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            //get the information associated with this notification
            val info = GarbageCollectionNotificationInfo.from(notification.getUserData().asInstanceOf[CompositeData])
            //get all the info and pretty print it

            // There is some indication from the documentation that getDuration returns milliseconds.
            // http://docs.oracle.com/javase/7/docs/jre/api/management/extension/com/sun/management/GcInfo.html#getDuration()
            // However!!!! emperical data indicates otherwise as does commentary in the original example.
            val duration = info.getGcInfo().getDuration() // microseconds

            var gcaction = info.getGcAction()
            val (gctype, isOld) = if ("end of minor GC".equals(gcaction)) {
              ("Young Gen GC", false)
            } else if ("end of major GC".equals(gcaction)) {
              ("Old Gen GC", true)
            } else {
              ("unknown", false)
            }

            // WP monitor and logging (note more info can be added as needed)
            if (isOld) {
              monitor !("gc", duration)
            } else {
              monitor !("ygc", duration)
            }


            def getMem(mem: Map[String, MemoryUsage]): Json = {
              val m = mem map {
                case (name, usage) =>
                  (name, JsonObject("used" -> usage.getUsed, "max" -> usage.getMax, "committed" -> usage.getCommitted))
              }
              m
            }

            if ((isOld && logOld) || (!isOld && logNew)) {
              val data = JsonObject("type" -> gctype, "id" -> info.getGcInfo().getId(), "name" -> info.getGcName(), "cause" -> info.getGcCause(),
                //"start" -> info.getGcInfo().getStartTime, "end" -> info.getGcInfo.getEndTime,
                "before" -> getMem(info.getGcInfo.getMemoryUsageBeforeGc.toMap[String, MemoryUsage]),
               "after" -> getMem(info.getGcInfo.getMemoryUsageAfterGc.toMap[String, MemoryUsage]),
                "duration" -> duration)


              if (! LoggingControl.stopping) log.alternative("gc", data)
            }

            //println("GcInfo CompositeType: " + info.getGcInfo().getCompositeType())
            /*
            //println("GcInfo MemoryUsageAfterGc: " + info.getGcInfo().getMemoryUsageAfterGc())
            //println("GcInfo MemoryUsageBeforeGc: " + info.getGcInfo().getMemoryUsageBeforeGc())

            //Get the information about each memory space, and pretty print it
            for (entry <- .entrySet) {
              val name = entry.getKey()
              val memdetail = entry.getValue()
              val memInit = memdetail.getInit()
              val memCommitted = memdetail.getCommitted()
              val memMax = memdetail.getMax()
              val memUsed = memdetail.getUsed()
              val before = membefore.get(name)
              val beforepercent = ((before.getUsed() * 1000L) / before.getCommitted())
              val percent = ((memUsed * 1000L) / before.getCommitted()) //>100% when it gets expanded

              //print(name + (if (memCommitted == memMax) "(fully expanded)" else "(still expandable)") +"used: " +
              //  (beforepercent / 10) + "." + (beforepercent % 10) + "%->" + (percent / 10) + "." +
              //  (percent % 10) + "%(" + ((memUsed / 1048576) + 1) + "MB) / ")
            }
            totalGcDuration += info.getGcInfo().getDuration()
            val percent = totalGcDuration * 1000L / info.getGcInfo().getEndTime()
            //println("GC cumulated overhead " + (percent / 10) + "." + (percent % 10) + "%")
            */
          }
        }
      }

      //Add the listener
      emitter.addNotificationListener(listener, null, null)
    }
  }
}