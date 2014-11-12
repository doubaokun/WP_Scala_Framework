package com.whitepages.framework.exceptions

import com.persist.JsonOps._
import com.whitepages.framework.exceptions.FrameworkException.stringify

/**
 * A request to the service was not legal.
 *
 * @param message the exception message.
 */
case class BadInputException(message: Json) extends FrameworkException(message)
