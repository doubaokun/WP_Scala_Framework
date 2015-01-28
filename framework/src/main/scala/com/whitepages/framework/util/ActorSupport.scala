package com.whitepages.framework.util

import com.whitepages.framework.logging.ActorLogging


/**
 * This trait should be included in Akka Actors to enable logging
 * and monitoring. Click the visibility All button to see protected
 * members that are defined here.
 * You might also want to un-click the Actor button.
 */

trait ActorSupport extends Support with ActorLogging

