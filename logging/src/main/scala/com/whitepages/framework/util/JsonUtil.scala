package com.whitepages.framework.util

import com.persist.JsonOps._

object JsonUtil {

  def safeCompact(j: Json) = {
    try {
      Compact(j)
    } catch {
      case ex: Throwable =>
        Compact(j, safe=true)
    }
  }

  def safePretty(j: Json) = {
    try {
      Pretty(j, width = 100, count = 4)
    } catch {
      case ex: Throwable =>
        Pretty(j, width = 100, count = 4, safe = true)
    }
  }

}
