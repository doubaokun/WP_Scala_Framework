package com.whitepages.framework.client

import akka.actor.{Actor, PoisonPill, ActorRef}
import scala.language.postfixOps
import java.sql.{PreparedStatement, DriverManager, ResultSet}
import java.sql.Connection
import org.postgresql.util.PSQLException
import com.persist.JsonOps._
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ActorSupport

private[client] case class PostgresRequest(sql: String, before: Option[(PreparedStatement) => Json], after: (ResultSet) => Json)

private[client] case class PostgresResult(before: Json, after: Json)

private[client] class PostgresActor(driverMessages: DriverMessages[PostgresRequest, PostgresResult],
                    host: String, port: Int,
                    user: String, password: String, dbName: String,
                    clientName: String) extends Actor with ActorSupport {

  import driverMessages._

  private[this] val balancer: ActorRef = context.parent
  private[this] val url = s"jdbc:postgresql://$host:$port/$dbName"

  val conn: Connection = try {
    Option(DriverManager.getConnection(url, user, password)) match {
      case Some(conn) =>
        balancer ! DriverReady
        conn
      case None =>
        throw new Exception(s"null result")
    }
  } catch {
    case ex: Throwable =>
      val reason = ex match {
        case psqlex: PSQLException =>
          s"Postgres connect failed; state: ${psqlex.getSQLState} ${psqlex.getMessage}"
        case ex: Throwable =>
          s"Postgres connect failed: ${ex.getMessage}"
      }
      balancer ! DriverConnectFailed(reason)
      //if (! conn.isClosed) conn.close()
      self ! PoisonPill
      null
  }

  def receive: PartialFunction[Any, Unit] = {

    case DriverSend(request, id, uid) =>
      if (conn.isClosed) {
        balancer ! DriverFail("Postgres connection closed")
        self ! PoisonPill
      } else {
        try {
          val response = request.before match {
            case None =>
              val stmt = conn.createStatement()
              val rs = stmt.executeQuery(request.sql)
              val aj = request.after(rs)
              rs.close()
              stmt.close()
              PostgresResult(jnull, aj)
            case Some(before1) =>
              val stmt = conn.prepareStatement(request.sql)
              val bj = before1(stmt)
              val rs = stmt.executeQuery()
              val aj = request.after(rs)
              rs.close()
              stmt.close()
              PostgresResult(bj, aj)
          }
          balancer ! DriverAck
          balancer ! DriverReceive(response, uid)
        } catch {
          case ex: Throwable =>
            balancer ! DriverAck
            balancer ! DriverReceiveFail("Postgres send failed", uid)
        }
      }

    case DriverHealthCheck(uid) =>
      if (conn.isClosed) {
        balancer ! DriverFail("Postgres connection closed")
        self ! PoisonPill
      } else {
        try {
          val stmt = conn.createStatement()
          val sql = "select 1"
          val rs = stmt.executeQuery(sql)
          balancer ! DriverAck
          balancer ! DriverHealthResult(true, uid)
        } catch {
          case ex: Throwable =>
            balancer ! DriverAck
            balancer ! DriverHealthResult(false, uid)
        }
      }

    case DriverClose =>
      if (! conn.isClosed) conn.close()
      self ! PoisonPill

    case x: Any =>
      log.error(noId, "Unexpected Postgres actor message: " + x)
  }
}

