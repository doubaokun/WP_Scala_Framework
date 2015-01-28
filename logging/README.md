scala-logging
=============

This library provides an advanced logging facility for Scala applications.
It is a part of the Whitepages Scala Framework but can also be used on its
own. It has the following features.

* All log messages are routed to a single Akka Actor.
* Captures logging messages from its own advanced Scala API, Java Slf4j and Akka loggers and sends them to that actor.
* All messages are logged as Json.
* The Scala API supports trace ids that can be used to track a single request across multiple servies.
* In the Scala API messages can be arbitrary Json rather then just strings.
* The Scala API supports logging exceptions and their stack trace as Json.
* Logs can be sent to stdout (for dev), local log files or a special UDP appender appication.
* Stdout messages are logged with abbreviated info in a pretty format. Other logs log one message per line.
* Log messages have a timestamp set at the time of the log call.
* Logging within a Scala class logs the class name and line number.
* Logging inside an actor also logs the actor path.
* The logging level can be set dynamically.
* The logging level can be overriden on a per id basis.
* There a an optional logger that will log garbage collection events.
* There is an optional time logger that can be used to track subtimes of a complex operation. This can be used across futures and actor message sends.
* There is an "alternative" logger that can be used to create custom logs.
