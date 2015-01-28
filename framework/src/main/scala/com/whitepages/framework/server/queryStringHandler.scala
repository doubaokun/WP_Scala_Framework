package com.whitepages.framework.server

import com.persist.JsonOps.JsonObject


private[server] trait QueryStringHandler {
  /**
   * Takes a A JsonObject (map) representing the query string params->args and the corresponding command (string),
   * and returns a modified version of the map, including service-specific defaults, etc.
   * @return Map[String, Json] where each key was a query string paramter, and each value was the corresponding qs value(s),
   * modified to include defaults and other service-specific necessities
   */
  val handle: (JsonObject, String) => JsonObject
}

private[server] object DefaultQueryStringHandler extends QueryStringHandler {
  val handle = new ((JsonObject, String) => JsonObject) {
    def apply(jsonMapIn: JsonObject, cmd: String): JsonObject = jsonMapIn
  }
}
