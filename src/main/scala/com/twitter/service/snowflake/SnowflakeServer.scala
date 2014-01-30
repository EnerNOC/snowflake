/** Copyright 2010-2012 Twitter, Inc. */
package com.twitter.service.snowflake

import com.twitter.service.snowflake.gen._
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.zk.ZkClient
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.NodeExistsException
import scala.collection.mutable
import java.net.{InetSocketAddress, InetAddress}
import com.twitter.logging.Logger
import com.twitter.server.TwitterServer
import com.twitter.service.snowflake.client.SnowflakeClient
import com.twitter.util._
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.tracing.BufferingTracer
import com.twitter.app.Flags
import java.util.concurrent.TimeUnit
import org.apache.thrift.transport.TTransportException

case class Peer(hostname: String, port: Int)

object SnowflakeServer extends TwitterServer {
  implicit val timer = new JavaTimer(true);

  val zkHosts = flag("snowflake.zookeeper.hosts", "localhost:2181", "Comma-separated list of ZooKeeper hosts (host1:port1,host2:port2)")
  val zkConnectTimeout = flag("snowflake.zookeeper.connectTimeout", 1000, "ZK connect timeout in milliseconds")
  val zkSessionTimeout = flag("snowflake.zookeeper.sessionTimeout", 5000, "ZK session timeout in milliseconds")
  val skipSanityChecks = flag("snowflake.skipSanityChecks", false, "Skip sanity checks")
  val serverPort = flag("snowflake.serverPort", 7609, "Port to bind snowflake to")
  val dataCenterId = flag("snowflake.dataCenterId", 0, "ID for this data center")
  val workerId = flag("snowflake.workerId", 0, "ID of snowflake worker")
  val workerZkPath = flag("snowflake.workerZkPath", "/snowflake-servers", "ZooKeeper path for snowflake")
  val startupSleepMs = flag("snowflake.startUpSleepMs", 10000, "Time to delay snowflake server start-up in milliseconds")

  val readTimeout = flag("snowflake.server.readTimeout", 1000, "Server read timeout in milliseconds")
  val requestTimeout = flag("snowflake.server.requestTimeout", 1000, "Server request timeout in milliseconds")
  val writeCompleteTimeout = flag("snowflake.server.writeCompleteTimeout", 1000, "Server write complete timeout in milliseconds")

  val fScribeCategory = flag("snowflake.scribe.category", "snowflake", "Scribe category")
  val fScribeHost = flag("snowflake.scribe.host", "localhost", "Scribe host")
  val fScribePort = flag("snowflake.scribe.port", 1463, "Scribe port")
  val fScribeSocketTimeout = flag("snowflake.scribe.socketTimeout", 5000, "Scribe socket timeout in milliseconds")
  val fFlushQueueLimit = flag("snowflake.scribe.flushQueueLimit", 100000, "Flush scribe queue after x messages")

  def apply(serverPort: Int, datacenterId: Int, workerId: Int, workerIdZkPath: String,
            skipSanityChecks: Boolean, startupSleepMs: Int, reporter: Reporter,
            zkClient: ZkClient, readTimeout: Int, requestTimeout: Int, writeCompleteTimeout: Int) = {

        statsReceiver.addGauge("datacenter_id") { datacenterId }
        statsReceiver.addGauge("worker_id") { workerId }

        new SnowflakeServer(serverPort, datacenterId, workerId, workerIdZkPath,
          skipSanityChecks, startupSleepMs, reporter, zkClient, readTimeout,
          requestTimeout, writeCompleteTimeout)
  }

  def main() {

    val reporterConfig = new ReporterConfig {
      scribeCategory = fScribeCategory()
      scribeHost = fScribeHost()
      scribePort = fScribePort()
      scribeSocketTimeout = fScribeSocketTimeout()
      flushQueueLimit = fFlushQueueLimit()
    }

    val zkClient = ZkClient(zkHosts(),
        Duration(zkConnectTimeout(), TimeUnit.MILLISECONDS),
        Duration(zkSessionTimeout(), TimeUnit.MILLISECONDS))

    val snowflake = SnowflakeServer(
        serverPort(),
        dataCenterId(),
        workerId(),
        workerZkPath(),
        skipSanityChecks(),
        startupSleepMs(),
        reporterConfig(),
        zkClient,
        readTimeout(),
        requestTimeout(),
        writeCompleteTimeout()
    )
    snowflake.start
    Await.ready(adminHttpServer)

    onExit {
      log.info("Closing snowflake server")
      snowflake.server map { s => s.close() }

      log.info("Closing admin server")
      adminHttpServer.close()
    }
  }
}

class SnowflakeServer(serverPort: Int, datacenterId: Int, workerId: Int, workerIdZkPath: String,
      skipSanityChecks: Boolean, startupSleepMs: Int, reporter: Reporter,
      zkClient: ZkClient, readTimeout: Int, requestTimeout: Int, writeCompleteTimeout: Int) {

  private[this] val log = Logger.get
  var server: Option[Server] = None

  def start {
    if (!skipSanityChecks) {
      sanityCheckPeers()
    }

    registerWorkerId(workerId)

    Thread.sleep(startupSleepMs)

    try {
      // aka processor
      val worker = new IdWorker(workerId, datacenterId, reporter)

      val service = new Snowflake.FinagledService(worker, new TBinaryProtocol.Factory())

      // Not sure I need this. Does twitter-server + netty take care of this?
//      val transport = new TNonblockingServerSocket(serverPort)
//      val serverOpts = new THsHaServer.Args(transport)
//      serverOpts.workerThreads(thriftServerThreads)

      //val server = new THsHaServer(service)
      log.info("Starting snowflake server on port %s", serverPort)
      server = Some(ServerBuilder()
        .bindTo(new InetSocketAddress(serverPort))
        .codec(ThriftServerFramedCodec())
        .tracer(new BufferingTracer)
        .requestTimeout(Duration(requestTimeout, TimeUnit.MILLISECONDS))
        .readTimeout(Duration(readTimeout, TimeUnit.MILLISECONDS))
        .writeCompletionTimeout(Duration(writeCompleteTimeout, TimeUnit.MILLISECONDS))
        .name("snowflake")
        .build(service))

      //log.info("Starting server on port %s with workerThreads=%s", serverPort, serverOpts.getWorkerThreads)
      log.info("Starting admin server on port 9990")
    } catch {
      case e: Exception => {
        log.error(e, "Unexpected exception while initializing server: %s", e.getMessage)
        throw e
      }
    }
  }

  def peers(): mutable.HashMap[Int, Peer] = {
    var peerMap = new mutable.HashMap[Int, Peer]

    try {
      zkClient(workerIdZkPath).getData
    } catch {
      case _:Throwable => {
        log.info("%s missing, trying to create it", workerIdZkPath)
        zkClient.withMode(CreateMode.PERSISTENT)(workerIdZkPath).create(Array())
      }
    }

    // TODO: Is name the correct piece of data?
    var numChildren = 0
    val children = zkClient(workerIdZkPath).getChildren()
    children foreach { i =>
      val peer = zkClient("%s/%s".format(workerIdZkPath, i.name)).getData()
      peer onSuccess { p =>
        val list = new String(p.formatted("%s")).split(':')
        peerMap(i.name.toInt) = new Peer(new String(list(0)), list(1).toInt)
        numChildren += 1
      }
    }
    log.info("found %s children".format(numChildren))

    return peerMap
  }

  def sanityCheckPeers() {
    var peerCount = 0
    val timestamps = peers().filter{ case (id: Int, peer: Peer) =>
      !(peer.hostname == getHostname && peer.port == serverPort)
    }.map { case (id: Int, peer: Peer) =>
      try {
        log.info("connecting to %s:%s".format(peer.hostname, peer.port))

        val c = SnowflakeClient(peer.hostname, peer.port, 1000)

        c.getWorkerId() onSuccess { reportedWorkerId =>
          if (reportedWorkerId != id) {
            log.error("Worker at %s:%s has id %d in zookeeper, but via rpc it says %d", peer.hostname, peer.port, id, reportedWorkerId)
            throw new IllegalStateException("Worker id insanity.")
          }
        }

        c.getDatacenterId() onSuccess { reportedDatacenterId =>
          if (reportedDatacenterId != datacenterId) {
            log.error("Worker at %s:%s has datacenter_id %d, but ours is %d",
              peer.hostname, peer.port, reportedDatacenterId, datacenterId)
            throw new IllegalStateException("Datacenter id insanity.")
          }
        }

        peerCount = peerCount + 1
        c.getTimestamp()
      } catch {
        case e: TTransportException => {
          log.error("Couldn't talk to peer %s at %s:%s", workerId, peer.hostname, peer.port)
          throw e
        }
      }
    }

    val peerTimestamps = Future.collect(timestamps.toSeq)
    peerTimestamps map { ts =>
      val avg = ts.foldLeft(0L)(_ + _) / peerCount
      if (math.abs(System.currentTimeMillis - avg) > 10000) {
        log.error("Timestamp sanity check failed. Mean timestamp is %d, but mine is %d, " +
          "so I'm more than 10s away from the mean", avg, System.currentTimeMillis)
        throw new IllegalStateException("timestamp sanity check failed")
      }
    }
  }

  def registerWorkerId(i: Int):Unit = {
    log.info("trying to claim workerId %d", i)
    var tries = 0
    while (true) {
      try {
        zkClient.withMode(CreateMode.EPHEMERAL)("%s/%s".format(workerIdZkPath, i)).create((getHostname + ':' + serverPort).getBytes())
        return
      } catch {
        case e: NodeExistsException => {
          if (tries < 2) {
            log.info("Failed to claim worker id. Gonna wait a bit and retry because the node may be from the last time I was running.")
            tries += 1
            Thread.sleep(1000)
          } else {
            throw e
          }
        }
      }
      log.info("successfully claimed workerId %d", i)
    }
  }

  def getHostname(): String = InetAddress.getLocalHost().getHostName()
}
