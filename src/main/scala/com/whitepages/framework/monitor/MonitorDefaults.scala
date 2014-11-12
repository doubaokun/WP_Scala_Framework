package com.whitepages.framework.monitor

import com.codahale.metrics.MetricRegistry

private[framework] object MonitorDefaults {
     def ext(metrics:MetricRegistry):PartialFunction[Any,Unit] = {
       {
         case Unit =>
       }
     }
}
