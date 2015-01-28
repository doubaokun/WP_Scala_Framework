package com.whitepages.framework.server

import com.persist.JsonOps._
import scala.io.Source
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport

// This class has mutable state.
// It is only accessed inside the server actor
private[server] case class Dynamic(isDev:Boolean) extends ClassSupport {

  private[this] var current: JsonObject = emptyJsonObject
  private[this] var schema: JsonObject = emptyJsonObject
  private[this] var dyn: JsonObject = emptyJsonObject
  //private[this] val config = system.settings.config
  private[this] val logDynamic = config.getBoolean("wp.service.logDynamic")

  private def isValid(v: Json, t: Json) = {
    (t, v) match {
      case ("boolean", b: Boolean) => true
      case ("string", s: String) => true
      case ("int", i: Int) => true
      case (t1: JsonArray, v1) => {
        t1.exists {
          case e: String => e == v1
          case x: Any => false
        }
      }
      case x: Any => false
    }
  }

  def init {
    try {
      val source = Source.fromURL(getClass.getResource("/deploy/dynamic.json")).getLines().mkString("\n")
      val j = Json(source)
      val j1 = jgetObject(j)
      schema = j1 map {
        case (n, v) => (n -> jget(v, "type"))
      }
      current = j1 map {
        case (n, v) =>
          val v1 = if (isDev) {
            if (jhas(v, "dev")) jget(v, "dev") else jget(v, "default")
          } else {
            jget(v, "default")
          }
          if (isValid(v1, jget(v, "type"))) (n -> v1) else (n -> null)
      }
      dyn = j1 map {
        case (n,v) =>
          (n->jgetBoolean(v,"request"))
      }
    } catch {
      case ex: Throwable =>
    }
    if (logDynamic) log.info(noId, JsonObject("InitialDynamic"->current))
  }

  def set(j: JsonObject) {
    j map {
      case (n, v) =>
        if (jhas(schema, n)) {
          if (isValid(v, jget(schema, n))) {
            current = current + (n -> v)
          }
        }
    }
    if (logDynamic) log.info(noId, JsonObject("NewDynamic"->current))
  }

  def get(dyns: JsonObject): JsonObject = {
    var current1 = current
    dyns foreach {
      case (n, v) =>
        if (jhas(schema, n)) {
          if (jgetBoolean(dyn,n) && isValid(v, jget(schema, n))) {
            current1 = current1 + (n -> v)
          }
        }
    }
    current1
  }
  init
}
