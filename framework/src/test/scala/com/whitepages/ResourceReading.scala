package com.whitepages

import scala.io.Source

trait ResourceReading {

  def getConfigFromResource(name: String) = {
    val stream = Thread.currentThread().getContextClassLoader.getResourceAsStream(name)
    val lines = Source.fromInputStream(stream).getLines()
    lines.foldLeft(new StringBuilder()) {
      case (sb, l) => sb.append(s"$l\n")
    }.toString()
  }
}
