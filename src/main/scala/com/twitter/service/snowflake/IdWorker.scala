/** Copyright 2010-2012 Twitter, Inc.*/
package com.twitter.service.snowflake

import com.twitter.service.snowflake.gen._
import java.util.Random
import com.twitter.logging.Logger
import com.twitter.finagle.stats.{Counter, LoadedStatsReceiver}
import com.twitter.finagle.tracing.Tracer
import com.twitter.util.Future

/**
 * An object that generates IDs.
 * This is broken into a separate class in case
 * we ever want to support multiple worker threads
 * per process
 */
class IdWorker(val workerId: Long, val datacenterId: Long, private val reporter: Reporter, var sequence: Long = 0L)
extends Snowflake.FutureIface {
  val statsReceiver = LoadedStatsReceiver.scope("snowflake")

  private[this] val exceptionCounter = statsReceiver.counter("exceptions")
  private[this] val idsGeneratedCounter = statsReceiver.counter("ids_generated")
  private[this] val log = Logger.get
  private[this] val rand = new Random
  private[this] var userAgentCounters = collection.mutable.Map[String, Counter]()

  private[this] def genCounter(agent: String) = {
    idsGeneratedCounter.incr()

    // TODO: How to replace this?
    //Stats.incr("ids_generated_%s".format(agent))
    val uaCounterKey = "ids_generated_%s".format(agent)
//    val uaCounter = userAgentCounters getOrElseUpdate(uaCounterKey, statsReceiver.counter(uaCounterKey))
//    uaCounter.incr()
    statsReceiver.counter(uaCounterKey).incr()
  }

  val twepoch = 1288834974657L

  private[this] val workerIdBits = 5L
  private[this] val datacenterIdBits = 5L
  private[this] val maxWorkerId = -1L ^ (-1L << workerIdBits)
  private[this] val maxDatacenterId = -1L ^ (-1L << datacenterIdBits)
  private[this] val sequenceBits = 12L

  private[this] val workerIdShift = sequenceBits
  private[this] val datacenterIdShift = sequenceBits + workerIdBits
  private[this] val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits
  private[this] val sequenceMask = -1L ^ (-1L << sequenceBits)

  private[this] var lastTimestamp = -1L

  // sanity check for workerId
  if (workerId > maxWorkerId || workerId < 0) {
    exceptionCounter.incr(1)
    throw new IllegalArgumentException("worker Id can't be greater than %d or less than 0".format(maxWorkerId))
  }

  if (datacenterId > maxDatacenterId || datacenterId < 0) {
    exceptionCounter.incr(1)
    throw new IllegalArgumentException("datacenter Id can't be greater than %d or less than 0".format(maxDatacenterId))
  }

  log.info("worker starting. timestamp left shift %d, datacenter id bits %d, worker id bits %d, sequence bits %d, workerid %d",
    timestampLeftShift, datacenterIdBits, workerIdBits, sequenceBits, workerId)

  // Implement interface methods
  def getId(useragent: String): Future[Long] = {
    if (!validUseragent(useragent)) {
      exceptionCounter.incr(1)
      throw new InvalidUserAgentError(useragent)
    }

    val id = nextId()
    genCounter(useragent)

    reporter.report(AuditLogEntry(id, useragent, rand.nextLong))
    Future.value(id)
  }

  def getWorkerId(): Future[Long] = Future.value(workerId)
  def getTimestamp(): Future[Long] = Future.value(System.currentTimeMillis)
  def getDatacenterId(): Future[Long] = Future.value(datacenterId)



  protected[snowflake] def nextId(): Long = synchronized {
    var timestamp = timeGen()

    if (timestamp < lastTimestamp) {
      exceptionCounter.incr(1)
      log.error("clock is moving backwards.  Rejecting requests until %d.", lastTimestamp);
      throw new InvalidSystemClock("Clock moved backwards.  Refusing to generate id for %d milliseconds".format(
        lastTimestamp - timestamp))
    }

    if (lastTimestamp == timestamp) {
      sequence = (sequence + 1) & sequenceMask
      if (sequence == 0) {
        timestamp = tilNextMillis(lastTimestamp)
      }
    } else {
      sequence = 0
    }

    lastTimestamp = timestamp
    ((timestamp - twepoch) << timestampLeftShift) |
      (datacenterId << datacenterIdShift) |
      (workerId << workerIdShift) | 
      sequence
  }

  protected def tilNextMillis(lastTimestamp: Long): Long = {
    var timestamp = timeGen()
    while (timestamp <= lastTimestamp) {
      timestamp = timeGen()
    }
    timestamp
  }

  protected def timeGen(): Long = System.currentTimeMillis()

  val AgentParser = """([a-zA-Z][a-zA-Z\-0-9]*)""".r

  def validUseragent(useragent: String): Boolean = useragent match {
    case AgentParser(_) => true
    case _ => false
  }
}
