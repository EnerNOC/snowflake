resolvers += "twitter-repo" at "http://maven.twttr.com"

resolvers += Resolver.sonatypeRepo("public")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.twitter" %% "scrooge-sbt-plugin" % "3.13.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")