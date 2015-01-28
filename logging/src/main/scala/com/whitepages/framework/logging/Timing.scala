package com.whitepages.framework.logging

import scala.util.Random

trait Timing {
  /**
   * This object contains methods used to mark regions to be timed in the timing log.
   */
  protected object time {
    /**
     * Call this method to start timing a region of code for the time log.
     * @param id the request id to be timed.
     * @param name each region to be timed must have a different name.
     * @param uid optional, if the region is executed multiple times, each occurrence must have a different uid.
     */
    def start(id: RequestId, name: String, uid: String = "") {
      if (LoggingState.doTime) LoggingState.timeStart(id, name, uid)
    }

    /**
     * Call this method to end timing a region of code for the time log.
     * The id, name, and uid must match the corresponding start.
     * @param id the request id to be timed.
     * @param name each region to be timed must have a different name.
     * @param uid optional, if the region is executed multiple times, each occurrence must have a different uid.
     */
    def end(id: RequestId, name: String, uid: String = "") {
      if (LoggingState.doTime) LoggingState.timeEnd(id, name, uid)
    }
  }

  /**
   * This method wraps a section of code, so that timings for it will appear in the time log.
   * @param id the requestId to be timed.
   * @param name  each region to be timed must have a different name.
   * @param body  the code to be timed.
   * @tparam T  the result type of the body.
   * @return  the value of the body. If the body throws an exception than that exception will pass through the Time call.
   */
  protected def Time[T](id: RequestId, name: String)(body: => T): T = {
    if (LoggingState.doTime) {
      val uid = Random.nextLong().toHexString
      LoggingState.timeStart(id, name, uid)
      try {
        body
      } finally {
        LoggingState.timeEnd(id, name, uid)
      }
    } else {
      body
    }
  }
}
