import AssemblyKeys._
import sbtassembly.Plugin.{PathList, MergeStrategy}

assemblySettings

com.twitter.scrooge.ScroogeSBT.newSettings

organization  := "enernoc"

version       := "1.0.2-SNAPSHOT"

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

//resolvers += "sonatype" at "https://oss.sonatype.org/content/groups/public"

libraryDependencies ++= Seq(
  "org.apache.thrift" % "libthrift" % "0.8.0",
  "com.twitter" %% "scrooge-core" % "3.11.1",
  "com.twitter" % "twitter-server_2.10" % "1.3.1",
  "com.twitter" % "util-zk_2.10" % "6.7.0",
  "com.twitter" % "util-zk-common_2.10" % "6.8.1",
  "com.twitter" % "util-zk-common_2.10" % "6.8.1",
  "com.twitter" % "ostrich_2.10" % "9.2.1",
  "org.specs2" %% "specs2" % "2.3.6" % "test",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
)

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"