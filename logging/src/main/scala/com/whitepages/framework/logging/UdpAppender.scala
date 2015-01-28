package com.whitepages.framework.logging

import akka.actor.{ActorSystem, Terminated, ActorRef, Actor}
import akka.io.{IO, Udp}
import java.net.InetSocketAddress
import akka.util.ByteString
import com.persist.JsonOps._

private[logging] class UdpAppender(host: String, port: Int, udpMtuSize: Long) extends ActorLogging {
  private[this] implicit val system = context.system
  private[this] val remote = new InetSocketAddress(host, port)
  private[this] var udp: ActorRef = null

  IO(Udp) ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady =>
      udp = sender
      context.watch(sender)
    case msg: String =>
      if (udp != null) {
        val bytes = ByteString(msg)
        if (bytes.size > udpMtuSize-100) {
          val bad = JsonObject("msg" -> "Msg too big for UDP",
            "msgPrefix" -> msg.substring(0, 500),
            "mtuSize" -> udpMtuSize,
            "msgSize" -> bytes.size)
          log.error(noId, bad)
        } else {
          udp ! Udp.Send(bytes, remote)
        }
      }
    case Udp.CommandFailed => log.error(noId, "UDP commands failed")
    case Terminated(x) =>
      log.error(noId, JsonObject("UDP terminated" -> x.path.toString))
      udp = null
    case x: Any => log.error(noId, JsonObject("unexpected UDP cmd" -> x.toString))
  }

  override def postStop {
    //log.error(noId, "UDP actor stopped")
  }
}
