package com.whitepages.framework.logging

import java.io.{PrintWriter, BufferedOutputStream, FileOutputStream, File}
import akka.actor._
import scala.concurrent.duration._
import scala.Some
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

private[logging] object FileAppender {

  trait AppendMessages

  case class AppendAdd(data: String, line: String) extends AppendMessages

  case object AppendClose extends AppendMessages

  case object AppendFlush extends AppendMessages

}

private[logging] class FileAppenderActor(serviceName:String, path: String, category: String) extends Actor with ActorLogging {
  private[this] val system = context.system
  private[this] implicit val ec: ExecutionContext = context.dispatcher
  private[this] var lastDate: String = ""
  private[this] var optw: Option[PrintWriter] = None
  import FileAppender._

  private[this] var flushTimer: Option[Cancellable] = None


  private def scheduleFlush() {
    val time = system.scheduler.scheduleOnce(2 seconds) {
      self ! AppendFlush
    }
    flushTimer = Some(time)
  }

  scheduleFlush()

  private def open(date: String) {
    val dir = s"$path/$serviceName"
    new File(dir).mkdirs()
    val fname = s"$dir/$category.$date.log"
    val w = new PrintWriter(new BufferedOutputStream(new FileOutputStream(fname, true)))
    optw = Some(w)
    lastDate = date
  }

  def receive = {
    case AppendAdd(date, line) =>
      optw match {
        case Some(w) =>
          if (date != lastDate) {
            w.close()
            open(date)
          }
        case None =>
          open(date)
      }
      optw match {
        case Some(w) =>
          w.println(line)
        case None =>
      }
    case AppendFlush =>
      optw match {
        case Some(w) =>
          w.flush()
        case None =>
      }
      scheduleFlush()

    case AppendClose =>
      flushTimer match {
        case Some(t) => t.cancel()
        case None =>
      }
      optw match {
        case Some(w) =>
          //w.flush()
          w.close()
          optw = None
        case None =>
      }
      // TODO use close?
    self ! PoisonPill
    case x: Any => log.warn(noId, "Bad appender message: " + x)

  }

  override def postStop() {
    optw match {
      case Some(w) =>
        //w.flush()
        w.close()
        optw = None
      case None =>
    }

  }
}

private[logging] case class FileAppender(actorRefFactory: ActorRefFactory, serviceName:String, path: String, category: String) {
  import FileAppender._

  private[this] val fileAppenderActor = actorRefFactory.actorOf(Props(classOf[FileAppenderActor], serviceName, path, category), name = s"Appender.$category")

  def add(date: String, line: String) {
    fileAppenderActor ! AppendAdd(date, line)
  }

  // Return Future[Unit]
  def close() {
    fileAppenderActor ! AppendClose
  }
}
