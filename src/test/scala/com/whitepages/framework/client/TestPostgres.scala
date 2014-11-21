package com.whitepages.framework.client

import com.persist.JsonOps._
import scala.concurrent.Await
import scala.concurrent.duration._
import java.sql.ResultSet
import com.whitepages.TestMain
import org.scalatest.{BeforeAndAfterAll, Matchers, FunSpec}
import com.whitepages.framework.service.ClientCallback
import com.whitepages.framework.logging.noId
import com.whitepages.framework.util.ClassSupport
import scala.language.postfixOps

/*
class TestPostgres extends FunSpec with Matchers with ClassSupport with BeforeAndAfterAll {
  private[this] val main = new TestMain()
  private[this] var client: PostgresClient = null
  private[this] var info: ClientCallback.Info = null

  override def beforeAll {
    info = main.testBefore(TestMain.testConfig)
    client = PostgresClient(info.refFactory, "dir_edit_ro")
  }

  override def afterAll {
    Await.result(client.stop, 1 minute)
    main.testAfter()
  }


  def resultHandler(rs: ResultSet): Json = {
    var result = emptyJsonObject
    while (rs.next()) {
      val s = rs.getString("short_id")
      result = Map("short_id" -> s) ++ result
      println("RESULT:" + s)
      s should equal("e0000w8")
    }
    result
  }

  describe("Postgres") {
    it("Postgres Test") {
      val sql = "select ss.short_id from short_id_reservations ss where ss.reservation_id = 16"
      val f1 = client.callStatement(sql, noId, resultHandler)
      Await.result(f1, 60 seconds)
    }
  }
}
*/