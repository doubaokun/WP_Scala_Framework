package com.whitepages.framework.server

private[server] object QsToJson {

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
}
