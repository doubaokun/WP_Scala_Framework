package com.whitepages.framework.exceptions

import com.persist.JsonOps._
import com.whitepages.framework.exceptions.FrameworkException.stringify

/**
 * Thrown when a client times out after it last retry.
 * @param message the exception message
 */
case class ClientTimeoutException(message: Json) extends FrameworkException(message)
