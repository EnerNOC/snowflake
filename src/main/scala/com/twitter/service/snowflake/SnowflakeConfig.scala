package com.twitter.service.snowflake

// TODO: Replace with a plain old Scala object, probably in SnowflakeServer.main

//import com.twitter.ostrich.admin.RuntimeEnvironment
//import com.twitter.ostrich.admin.config.ServerConfig
//import com.twitter.zk.ZkClient
//import java.net.InetAddress
//import com.twitter.util.Config.RequiredValuesMissing
//
//trait SnowflakeConfig extends ServerConfig[SnowflakeServer] {
//  var serverPort = 7609
//  var datacenterId = required[Int]
//  var workerIdMap = required[Map[Int, String]]
//  var workerIdZkPath = "/snowflake-servers"
//  var skipSanityChecks = false
//  var startupSleepMs = 10000
//  var thriftServerThreads = 2
//
//  var reporterConfig = required[ReporterConfig]
//
//  var zkClient = required[ZkClient]
//
//  def workerIdFor(host: InetAddress) = {
//    workerIdMap.mapValues(
//      name => name.split(':')(0)
//    ).find {
//      case(k,v) =>  v == host.getHostName.split(':')(0)
//    }.get._1
//  }
//
//  def apply(runtime: RuntimeEnvironment) = {
//    new SnowflakeServer(serverPort, datacenterId, workerIdFor(InetAddress.getLocalHost),
//        workerIdZkPath, skipSanityChecks, startupSleepMs, thriftServerThreads, reporterConfig(),
//        zkClient)
//  }
//
//  override def validate = {
//    if (workerIdMap.values.size != workerIdMap.values.toSet.size)
//      throw new RequiredValuesMissing("duplicate worker Ids")
//    reporterConfig.validate
//    super.validate
//  }
//}
