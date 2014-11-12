package com.whitepages.framework.exceptions


import com.persist.JsonOps._
import com.whitepages.framework.exceptions.FrameworkException.stringify

/**
 * Thrown when a request to a client fails.
 * @param message the exception message
 */
case class ClientFailException(message: Json) extends FrameworkException(message)
