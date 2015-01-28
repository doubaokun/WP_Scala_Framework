name := "scala-webservice"

organization := "com.whitepages"

scalaVersion := "2.11.1"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, javaSource, organization)

buildInfoPackage := "com.whitepages.info." + (name.value.replace("-", "_"))

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "riemann repo" at "http://clojars.org/repo"

// resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"

libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % "2.11.1",
        "com.whitepages" %% "scala-logging" % "1.1.0-SNAPSHOT",
        //"commons-daemon" % "commons-daemon" % "1.0.15",
        //"org.codehaus.janino" % "janino" % "2.6.1",
        "com.codahale.metrics" % "metrics-core" % "3.0.1",
        "com.codahale.metrics" % "metrics-graphite" % "3.0.1",
        "com.codahale.metrics" % "metrics-jvm" % "3.0.1",
        "com.aphyr" % "riemann-java-client" % "0.2.9",
        "com.typesafe.akka" %% "akka-actor" % "2.3.2",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "com.typesafe.akka" %% "akka-slf4j" % "2.3.2",
        "com.typesafe.akka" %% "akka-remote" % "2.3.2",
        //"com.persist" %% "persist-json" % "0.21",
        "io.spray" %% "spray-can" % "1.3.1-20140423",
        "joda-time" % "joda-time" % "2.2",
        "org.joda" % "joda-convert" % "1.2",
        "org.scalatest" %% "scalatest" % "2.1.7" % "test",
        //"postgresql" % "postgresql" % "9.1-901.jdbc4",
        "org.jfree" % "jfreechart" % "1.0.14" % "test")
        // "com.etaty.rediscala" %% "rediscala" % "1.3.2",
        //"com.etaty.rediscala" %% "rediscala" % "1.4.0",
        //"com.twitter" % "scrooge-core_2.11" % "3.16.42" exclude("org.apache.thrift","libthrift"),
        //"org.apache.thrift" % "libthrift" % "0.9.0",
        //"ch.qos.logback" % "logback-classic" % "1.0.13",
        //"com.persist" % "persist-json_2.11" % "0.21")
