import com.twitter.service.snowflake.{SnowflakeConfig, ReporterConfig}
import com.twitter.logging.config.{LoggerConfig, FileHandlerConfig}
import com.twitter.logging.Logger
import com.twitter.zookeeper.ZookeeperClientConfig
import java.net.InetAddress
import com.twitter.ostrich.admin.config.AdminServiceConfig

new SnowflakeConfig {
  serverPort = 7610
  datacenterId = 0
  workerIdMap = Map(1 -> InetAddress.getLocalHost.getHostName)
  workerIdZkPath = "/snowflake-servers"
  skipSanityChecks = false
  startupSleepMs = 10000
  thriftServerThreads = 2

  zookeeperClientConfig = new ZookeeperClientConfig {
    hostList = "localhost"
  }

  reporterConfig = new ReporterConfig {
    scribeCategory = "snowflake"
    scribeHost = "localhost"
    scribePort = 1463
    scribeSocketTimeout = 5000
    flushQueueLimit = 100000
  }

  admin = new AdminServiceConfig {
    httpPort = 9991
  }

  loggers = new LoggerConfig {
    handlers = new FileHandlerConfig {
      filename = "snowflake2.log"
      level = Logger.TRACE
    }
  }

}
