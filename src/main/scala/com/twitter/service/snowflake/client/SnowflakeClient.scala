/** Copyright 2010-2012 Twitter, Inc. */
package com.twitter.service.snowflake.client
import com.twitter.service.snowflake.gen.Snowflake
import com.twitter.finagle.builder.ClientBuilder
import java.net.InetSocketAddress
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.util.Duration
import java.util.concurrent.TimeUnit
import scala.reflect.runtime.universe.TypeTag
import com.twitter.finagle.tracing.BufferingTracer

// TODO: Is this the right way to build a client for this?
//object SnowflakeClient extends ThriftClient[Snowflake.FinagledClient]

object SnowflakeClient {
  def apply(host: String, port: Int, soTimeoutMs: Int)(implicit man: TypeTag[Snowflake.FinagledClient]) = {
    val service = ClientBuilder()
        .hosts(new InetSocketAddress(host, port))
        .codec(ThriftClientFramedCodec())
        .hostConnectionLimit(1)
        .timeout(Duration(soTimeoutMs, TimeUnit.MILLISECONDS))
        .tracer(new BufferingTracer)
        .build()
    val client = new Snowflake.FinagledClient(service, new TBinaryProtocol.Factory())
    client
  }
}

