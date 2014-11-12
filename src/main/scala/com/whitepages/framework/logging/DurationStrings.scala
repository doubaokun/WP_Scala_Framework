package com.whitepages.framework.logging

private[framework] object DurationStrings {

  /*
  def millisDuration(millis: Long): String = {
    val s = millis.toString()
    if (millis > 1000) {
      val split = s.length - 3
      s"""${s.substring(0, split)}.${s.substring(split)} secs"""
    } else {
      s"""$s millis"""
    }
  }

  def microsDuration(micros: Long): String = {
    if (micros > 1000000) {
      millisDuration(micros / 1000)
    } else {
      val s = micros.toString()
      if (micros > 1000) {
        val split = s.length - 3
        s"""${s.substring(0, split)}.${s.substring(split)} millis"""
      } else {
        s"""$s micros"""
      }
    }
  }
  */

  def millisDuration(millis:Long):Long = millis * 1000

  def microsDuration(micros: Long): Long= micros

}
