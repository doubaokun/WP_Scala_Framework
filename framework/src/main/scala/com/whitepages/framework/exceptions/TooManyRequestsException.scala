package com.whitepages.framework.exceptions

import com.persist.JsonOps._

/**
 * Indicates that the service is not available.
 * @param message the exception message
 */
case class TooManyRequestsException(message: Json) extends FrameworkException(message)
