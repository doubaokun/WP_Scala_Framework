package com.whitepages.framework.server

import java.net.URLDecoder

import com.persist.JsonOps._

private[framework] object SprayUtil {

  def qsToJson(queryString: String) = {
    val decoded = java.net.URLDecoder.decode(queryString, "UTF-8")
    val pairs = decoded.split("&").toList
    addPairs(pairs, Map())
  }

  def addPairs(pairs: Seq[String], map: Map[String, Any]): Map[String, Any] = pairs match {
    case p :: ps => addPairs(ps, addOne(p, map))
    case _ => map
  }

  def addOne(pair: String, map: Map[String, Any]): Map[String, Any] = {
    val kv = pair.split("=").toSeq
    if(kv(0).endsWith("[]")) {
      val key = kv(0).substring(0, kv(0).length - 2)
      val cur = map.getOrElse(key, Seq()).asInstanceOf[Seq[_]]
      map + ((key, cur:+ kv(1)))
    } else {
      map + ((kv(0), kv(1)))
    }
  }

  private def getServerOptions(query: spray.http.Uri.Query, prefix: String): Map[String, Any] = {

    val validMap = query.toMultiMap.filterKeys {
      k => k.startsWith(prefix)
    }
    validMap.map {
      case (k, v) => {
        val opt = k.substring(prefix.length).toLowerCase
        val parsedV = v match {
          case v1 :: Nil if v.contains(",") => v1.split(",").map(s => convertParam(s)).toList
          case v2 :: Nil => convertParam(v2)
          case v: List[String] => v.map(s => convertParam(s))
        }
        opt -> parsedV
      }
    }
  }

  def getServerOpts(query: spray.http.Uri.Query): Map[String, Any] = getServerOptions(query, "opt:")

  def getServerDyns(query: spray.http.Uri.Query): Map[String, Any] = getServerOptions(query, "dyn:")

  def convertParam(v1: String): Json = {
    val v = URLDecoder.decode(v1, "UTF8")
    v match {
      case jString if jString.startsWith("(") => {
        Json(jString.substring(1, jString.length - 1))
      }
      case x => x
    }
  }

  def stringifyJson(json: Json, opts: Map[String, Any]) = {
    if (opts.contains("pretty")) Pretty(json) else Compact(json)
  }

  def queryToJsonMap(query: spray.http.Uri.Query): Map[String, Json] = {
    query.toMultiMap.filterKeys {
      case x =>
        !x.startsWith("opt:") && !x.startsWith("dyn:")
    }.map {
      case (k, v :: Nil) => (k, convertParam(v))
      case (k, v: List[String]) => (k, v.map(p => convertParam(p)).reverse)
    }
  }

  case class Bind(listen: String, port: Integer)

  case object Unbind

  case object Drain
}
