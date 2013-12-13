package com.twitter.service.snowflake

import org.specs2.mutable._
import com.twitter.service.snowflake.gen.Snowflake
import com.twitter.service.snowflake.client.SnowflakeClient

class SnowflakeClientSpec extends SpecificationWithJUnit {
  val port: Int = 7609
  val host: String = "localhost"
  val timeout: Int = 5000
  val useragent: String = "SnowflakeClientSpec"

  "client" should {
    "be created" in {
      val client = SnowflakeClient(host, port, timeout)
      client must beAnInstanceOf[Snowflake.FinagledClient]
    }

    "get an id" in {
      val client = SnowflakeClient(host, port, timeout)
      val id = client.getId(useragent).get()
      println(id)
      id must be_>(0L)
    }

    "get a worker id" in {
      val client = SnowflakeClient(host, port, timeout)
      val id = client.getWorkerId().get()
      println(id)
      id mustEqual 0L
    }

    "get a datacenter id" in {
      val client = SnowflakeClient(host, port, timeout)
      val id = client.getDatacenterId().get()
      println(id)
      id mustEqual 0L
    }

    "get a timestamp" in {
      val client = SnowflakeClient(host, port, timeout)
      val ts = client.getTimestamp().get()
      println(ts)
      ts must be_>(0L)
    }
  }
}
