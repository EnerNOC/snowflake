package com.twitter.service.snowflake

import org.specs2.mutable._
import com.twitter.service.snowflake.gen.Snowflake
import com.twitter.service.snowflake.client.SnowflakeClient
import com.twitter.util.Await

class SnowflakeClientSpec extends SpecificationWithJUnit {
  val port: Int = 7609
  val host: String = "snowflake.mvp.enernoc.net"
  val timeout: Int = 25000
  val useragent: String = "SnowflakeClientSpec"

  "client" should {
    "be created" in {
      val client = SnowflakeClient(host, port, timeout)
      client must beAnInstanceOf[Snowflake.FinagledClient]
    }

    "get an id" in {
      val client = SnowflakeClient(host, port, timeout)
      val id = Await.result(client.getId(useragent))
      println(id)
      id must be_>(0L)
    }

    "get a worker id" in {
      val client = SnowflakeClient(host, port, timeout)
      val id = Await.result(client.getWorkerId())
      println(id)
      id mustEqual 0L
    }

    "get a datacenter id" in {
      val client = SnowflakeClient(host, port, timeout)
      val id = Await.result(client.getDatacenterId())
      println(id)
      id mustEqual 0L
    }

    "get a timestamp" in {
      val client = SnowflakeClient(host, port, timeout)
      val ts = Await.result(client.getTimestamp())
      println(ts)
      ts must be_>(0L)
    }
  }
}
