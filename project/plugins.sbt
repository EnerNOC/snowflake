resolvers += "twitter-repo" at "http://maven.twttr.com"

resolvers += "sonatype" at "https://oss.sonatype.org/content/groups/public"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.twitter" %% "scrooge-sbt-plugin" % "3.11.1")