name := "scala-logging"

organization := "com.whitepages"

scalaVersion := "2.11.1"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, javaSource, organization)

buildInfoPackage := "com.whitepages.info." + (name.value.replace("-", "_"))

libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % "2.11.1",
        "commons-daemon" % "commons-daemon" % "1.0.15",
        "org.codehaus.janino" % "janino" % "2.6.1",
        "com.typesafe.akka" %% "akka-actor" % "2.3.2",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "com.typesafe.akka" %% "akka-slf4j" % "2.3.2",
        "com.typesafe.akka" %% "akka-remote" % "2.3.2",
        "joda-time" % "joda-time" % "2.2",
        "org.joda" % "joda-convert" % "1.2",
        "ch.qos.logback" % "logback-classic"  % "1.0.13",
        "org.scalatest" %% "scalatest" % "2.1.7" % "test",
        "com.persist" % "persist-json_2.11" % "0.21")
