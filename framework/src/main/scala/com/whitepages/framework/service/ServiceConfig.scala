package com.whitepages.framework.service

import com.typesafe.config.{ConfigValueFactory, ConfigValue, ConfigFactory, Config}
import java.io.{FileWriter, File}
import scala.collection.JavaConversions._

private[service] object ServiceConfig {
  private def addLibraryPath(pathToAdd: String) {
    val usrPathsField = classOf[ClassLoader].getDeclaredField("usr_paths")
    usrPathsField.setAccessible(true);

    //get array of paths
    val paths = usrPathsField.get(null).asInstanceOf[Array[String]]

    //check if the path to add is already present
    for (path <- paths) {
      if (path.equals(pathToAdd)) {
        return
      }
    }

    //add the new path
    val newPaths = paths :+ pathToAdd
    usrPathsField.set(null, newPaths)
  }

  private def addProp(key1: String, value: String) {
    val key = key1.replaceAll("-", ".")
    System.setProperty(key, value)
    if (key == "java.library.path") addLibraryPath(value)
  }

  private def addProps(config: Config) {
    for (key <- config.root.keySet()) {
      addProp(key, config.getString(key))
    }
  }

  private def setJavaProperties(serviceName: String, config: Config) {
    val path1 = "wp.jprops"
    //val path2 = "wp." + serviceName + ".jprops"
    val logLocation = if (config.hasPath("logSvcName")) config.getString("logSvcName") else serviceName
    addProp("SVC_NAME", logLocation)
    if (config.hasPath(path1)) {
      val jprops1 = config.getConfig(path1)
      addProps(jprops1)
    }
    //if (config.hasPath(path2)) {
    //  val jprops2 = config.getConfig(path2)
    //  addProps(jprops2)
    //}
  }

  private def writeConfig(oc: String, config: Config) {
    val path = if (oc == null) "target/config" else oc
    val d = new File(path)
    d.mkdirs
    val f = new File(path + "/all.conf")
    val fw = new FileWriter(f)
    fw.write(config.root().render())
    fw.close()
  }

  def makeConfig(host:String, serviceName: String, debugConfig: Option[Config] = None,
                 isDocker: Boolean = false, select: Seq[String] = Seq[String]()): Config = {
    //val config = ConfigFactory.load()
    // Insert akka remote hostname
    var hostConfig: ConfigValue = ConfigValueFactory.fromAnyRef(host, "local host")
    val config = ConfigFactory.load().withValue("akka.remote.netty.tcp.hostname", hostConfig)

    val configws = ConfigFactory.parseResources("wp.conf")
    val config1 = ConfigFactory.parseResources(serviceName + ".conf")
    val config2 = config1.withFallback(configws).withFallback(config)
    val oc = System.getProperty("config-dir")
    val config3 = if (oc == null) {
      val home = System.getProperty("user.home")
      val ofile = new File(home + "/.wpscala/" + serviceName + ".conf")
      if (ofile.exists()) {
        val oconfig = ConfigFactory.parseFile(ofile)
        oconfig.withFallback(config2)
      } else {
        config2
      }
    } else {
      val ofile1 = new File(oc + "/" + serviceName + ".deploy.conf")
      val oconfig1 = if (ofile1.exists()) {
        val oc1 = ConfigFactory.parseFile(ofile1)
        oc1.withFallback(config2)
      } else {
        config2
      }
      val ofile2 = new File(oc + "/" + serviceName + ".override.conf")
      if (ofile2.exists()) {
        val oc2 = ConfigFactory.parseFile(ofile2)
        oc2.withFallback(oconfig1)
      } else {
        oconfig1
      }
    }
    val config4 = debugConfig match {
      case Some(d: Config) =>
        d.withFallback(config3)
      case None =>
        config3
    }
    val config5 = if (select.size == 0) {
      config4
    } else {
      //println(s"select=$serviceName-select.conf")
      val selectConfig = ConfigFactory.parseResources(s"$serviceName-select.conf")
      select.foldLeft(config4){
        (config, s) =>
          //println("s="+s)
          if (selectConfig.hasPath(s)) {
            val selected = selectConfig.getConfig(s)
            //println(selected.root.render())

            selected.withFallback(config)
          } else {
            config
          }
      }
    }
    if (!isDocker) writeConfig(oc, config5)
    setJavaProperties(serviceName, config5)
    config5
  }
}
