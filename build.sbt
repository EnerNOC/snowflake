import AssemblyKeys._
import sbtassembly.Plugin.{PathList, MergeStrategy}

assemblySettings

com.twitter.scrooge.ScroogeSBT.newSettings

val archiva = "http://archiva.admin.bdap.enernoc.net:8080/"

organization  := "com.twitter"

version       := "1.1-SNAPSHOT"

scalaVersion  := "2.10.3"

mainClass in (Compile, packageBin) := Some("com.twitter.service.snowflake.SnowflakeServer")

mainClass in (Compile, run) := Some("com.twitter.service.snowflake.SnowflakeServer")

mainClass in (Compile, assembly) := Some("com.twitter.service.snowflake.SnowflakeServer")

test in assembly := {}

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case PathList(ps @ _*) if ps.last endsWith ".txt.1" => MergeStrategy.discard
    case x => old(x)
  }
}

resolvers += "twitter repo" at "http://maven.twttr.com"

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies ++= Seq(
  "org.apache.thrift" % "libthrift" % "0.8.0",
  "com.twitter" %% "scrooge-core" % "3.13.2",
  "com.twitter" %% "twitter-server" % "1.6.1",
  "com.twitter" %% "util-zk" % "6.13.2",
  "com.twitter" %% "util-zk-common" % "6.13.2",
  "com.twitter" %% "ostrich" % "9.4.2",
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "org.specs2" %% "specs2" % "2.3.6" % "test"
)

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"