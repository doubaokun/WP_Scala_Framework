package com.whitepages.framework.exceptions

import com.persist.JsonOps._

/**
 * This object contains the unapply method needed to match the
 *  FrameworkException trait.
 */
object FrameworkException {
  /** The unapply for matching the FrameworkException trait.
    */
  def unapply(f: FrameworkException): Option[(Json)] = Some((f.msg))

  private[framework] def stringify(j: Json): String = {
    j match {
      case s: String => s
      case j: Json => Compact(j)
    }
  }
}

/**
 * The common parent of all framework exceptions.
 *
 * @param msg the Json exception message.
 */
abstract class FrameworkException(val msg:Json) extends Exception(FrameworkException.stringify(msg))

