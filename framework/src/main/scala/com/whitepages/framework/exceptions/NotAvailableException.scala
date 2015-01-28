package com.whitepages.framework.exceptions

import com.persist.JsonOps._
import com.whitepages.framework.exceptions.FrameworkException.stringify

/**
 * Indicates that the service is not available.
 * @param message the exception message
 */
case class NotAvailableException(message: Json) extends FrameworkException(message)
