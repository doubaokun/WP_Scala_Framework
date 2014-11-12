name := "scala-webservice"

organization := "com.whitepages"

repo := "search-dev"

scalaVersion := "2.11.1"

crossScalaVersions := Seq("2.11.1")   // sbt-release bug!

wpSettings

scalacOptions in (Compile, doc) ++= Seq("-skip-packages", "com.persist")

libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % "2.11.1",
        "com.whitepages" % "wp-logback-util" % "0.1.1",
        "commons-daemon" % "commons-daemon" % "1.0.15",
        "org.codehaus.janino" % "janino" % "2.6.1",
        "com.codahale.metrics" % "metrics-core" % "3.0.1",
        "com.codahale.metrics" % "metrics-graphite" % "3.0.1",
        "com.codahale.metrics" % "metrics-jvm" % "3.0.1",
        "com.aphyr" % "riemann-java-client" % "0.2.9",
        "com.typesafe.akka" %% "akka-actor" % "2.3.2",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "com.typesafe.akka" %% "akka-slf4j" % "2.3.2",
        "com.typesafe.akka" %% "akka-remote" % "2.3.2",
        "com.persist" %% "persist-json" % "0.21",
        "io.spray" %% "spray-can" % "1.3.1-20140423",
        "joda-time" % "joda-time" % "2.2",
        "org.joda" % "joda-convert" % "1.2",
        "com.whitepages" %% "scala-webservice-thrift" % "0.0.24" % "test",
        "org.scalatest" %% "scalatest" % "2.1.7" % "test",
        "postgresql" % "postgresql" % "9.1-901.jdbc4",
        "org.jfree" % "jfreechart" % "1.0.14" % "test",
        "com.etaty.rediscala" %% "rediscala" % "1.3.2",
        "com.twitter" %% "scrooge-core" % "3.16.42"
)
