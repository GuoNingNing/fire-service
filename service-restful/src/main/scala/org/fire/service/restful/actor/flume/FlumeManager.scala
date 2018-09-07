package org.fire.service.restful.actor.flume

import java.io.{File, FileInputStream, FileOutputStream, PrintWriter}
import java.net.InetAddress
import java.nio.file.{Files, Paths}
import java.util.{Properties, Map => JMap}
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.ConfigFactory
import org.fire.service.core.BaseActor
import org.fire.service.core.util.{Decompression, Utils}
import spray.json._
import org.fire.service.restful.actor.flume.FlumeManagerJsonFormat._

import scala.collection.JavaConversions._
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

/**
  * Created by cloud on 18/8/31.
  */
class FlumeManager extends BaseActor{

  import FlumeManager._

  private lazy val conf = ConfigFactory.load()
  private val instancePath =
    Option(conf.getString(ConfigConstant.INSTANCE_PATH)).getOrElse(ConfigConstant.INSTANCE_DEF_PATH)
  private val flumePath =
    Option(conf.getString(ConfigConstant.FLUME_PATH)).getOrElse(ConfigConstant.FLUME_DEF_PATH)
  private val startScript = conf.getString(ConfigConstant.START_SCRIPT)
  private val serverUrl = Option(conf.getString(ConfigConstant.SER_URL)).getOrElse("null")

  private val instances: JMap[String, FlumeStatus] = new ConcurrentHashMap[String, FlumeStatus]()

  override def preStart(): Unit = {
    loadInstance()
    import context.dispatcher
    context.system.scheduler.schedule(120 seconds, 180 seconds, self, FlumeHeartbeat)
  }

  override def receive: Receive = {
    // 心跳
    case FlumeHeartbeat => heartbeat()

    // 查询实例
    case FetchInstance(name) => fetchInstance(name)

    // 安装flume
    case flume: Flume => installFlume(flume)

    // 管理实例配置
    case instance: FlumeInstance => instanceManager(instance)

    // 安装flume jar包
    case jar: FlumeJar => installFlumeJar(jar)

    // 启动实例
    case startInstance: StartInstance => start(startInstance)

    // 停止实例
    case stopInstance: StopInstance => stop(stopInstance)

    // 查询实例状态
    case statusInstance: StatusInstance => status(statusInstance)
  }

  private def heartbeat(): Unit = {
    val ip = Try(InetAddress.getLocalHost.getHostAddress).getOrElse("")
    val timestamp = System.currentTimeMillis()
    val res = Utils.httpGet(s"$serverUrl?ip=$ip&timestamp=$timestamp").asString
    logger.info(s"send heartbeat http code ${res.code}")
    instances.foreach { case (k, f) =>
        f.setRun(getRunning(k))
    }
  }

  private def fetchInstance(name: String): Unit = {
    instances.containsKey(name) match {
      case true => sender ! FlumeResponse(0, instances.get(name).toJson.compactPrint)
      case false => sender ! FlumeResponse(0, instances.toMap.toJson.compactPrint)
    }
  }

  private def installFlume(flume: Flume): Unit = {
    Utils.getFile(flume.url,s"/tmp/flume.tar.gz") match {
      case true => Decompression.unTarGZIP(s"/tmp/flume.tar.gz",s"$flumePath") match {
        case true => sender ! FlumeResponse(0, s"install flume to $flumePath/flume success")
        case false => sender ! FlumeResponse(-1, s"Decompression flume.tar.gz failed")
      }

      case false => sender ! FlumeResponse(-1, s"http get file from ${flume.url} failed")
    }
  }

  private def installFlumeJar(flumeJar: FlumeJar): Unit = {
    Utils.getFile(flumeJar.url, s"$flumePath/flume/lib/${flumeJar.name}") match {
      case true => sender ! FlumeResponse(0, s"install jar ${flumeJar.name} to $flumePath/flume/lib success")
      case false => sender ! FlumeResponse(-1, s"install jar ${flumeJar.name} to $flumePath/flume/lib failed")
    }
  }

  private def instanceManager(flumeInstance: FlumeInstance): Unit = {
    instances.containsKey(flumeInstance.name) match {
      case true =>
        Try {
          flumeInstance.storeInstance(instancePath)
          instances.put(flumeInstance.name, FlumeStatus(flumeInstance, instances.get(flumeInstance.name).running))
        } match {
          case Success(s) =>
            sender ! FlumeResponse(0, s"update instance ${s.instance.name} success")
          case Failure(e) =>
            sender ! FlumeResponse(-1, s"update instance ${flumeInstance.name} failed. ${e.getMessage}")
        }

      case false =>
        Try{
          Files.createDirectories(Paths.get(s"$instancePath/${flumeInstance.name}"))
          flumeInstance.storeInstance(instancePath)
          instances.put(flumeInstance.name, FlumeStatus(flumeInstance, false))
        } match {
          case Success(s) =>
            sender ! FlumeResponse(0, s"create instance ${s.instance.name} success")
          case Failure(e) =>
            sender ! FlumeResponse(-1, s"create instance ${flumeInstance.name} failed. ${e.getMessage}")
        }
    }
  }

  private def service(action: String,name: String): Unit = {
    instances.containsKey(name) match {
      case true =>
        val result = run(s"$startScript", List(s"$instancePath/$name", action))
        result.exitCode match {
          case x if x > 0 => sender ! FlumeResponse(-1, s"$action $name failed.\n${result.stderr}")
          case _ => sender ! FlumeResponse(0, s"$action $name success.\n${result.stdout}")
        }
      case false =>
        var code = 0
        var stderr = ""
        instances.foreach { case (k,v) =>
            val res = run(s"$startScript", List(s"$instancePath/$k", action))
            if (res.exitCode != 0) {
              code += res.exitCode
              stderr += s"\n$k:${res.stderr}"
            }
        }
        if (code == 0) {
          sender ! FlumeResponse(0, s"$action all instance success.")
        } else {
          sender ! FlumeResponse(-1, s"$action failed.\n$stderr")
        }
    }
  }
  private def start(startInstance: StartInstance): Unit = service("start", startInstance.name)
  private def stop(stopInstance: StopInstance): Unit = service("stop", stopInstance.name)
  private def status(statusInstance: StatusInstance): Unit = service("status", statusInstance.name)

  private def loadInstance(): Unit = {
    val ins = new File(instancePath)
    ins.listFiles().filter(_.isDirectory).foreach(name => {
      val env = getEnv(s"${name.getAbsolutePath}/conf/${ConfigConstant.ENV_FILE}")
      val conf = propertiesToMap(propertiesLoad(s"${name.getAbsolutePath}/conf/${ConfigConstant.CONF_FILE}"))
      val log = propertiesToMap(propertiesLoad(s"${name.getAbsolutePath}/conf/${ConfigConstant.LOG_FILE}"))
      val isRun = getRunning(name.getName)
      instances.put(name.getName, FlumeStatus(FlumeInstance(name.getName,env,conf,log),isRun))
    })
  }

  private def getEnv(env: String): Map[String, String] = {
    scala.io.Source.fromFile(env).getLines().map { s =>
      s.split("=",2) match {
        case Array(k,v) => k.trim -> v.trim
        case Array(k) => k.trim -> ""
      }
    }.toMap
  }

  private def getRunning(name: String): Boolean = {
    val result = run(s"$startScript",List(s"$instancePath/$name", "status"))
    result.exitCode match {
      case x if x > 0 => false
      case _ => true
    }
  }

  private def run(cmd: String, args: List[String]): ProcessResult = {
    val process = Process(cmd, args)
    var (stdout,stderr) = ("","")
    ProcessResult(
      process.run(ProcessLogger(x => stdout = x, y => stderr = y)).exitValue(),
      stdout,
      stderr
    )
  }
}

object FlumeManager {
  val NAME = "FlumeManager"

  object ConfigConstant {
    // 配置文件名称
    val ENV_FILE = "flume-env.sh"
    val CONF_FILE = "flume-conf.properties"
    val LOG_FILE = "log4j.properties"

    // flume 安装路径
    val FLUME_PATH = "flume.manager.flume.path"
    val FLUME_DEF_PATH = s"${System.getProperty("user.home")}"

    // 启动脚本路径
    val START_SCRIPT = "flume.manager.start.script"

    // flume 实例安装路径
    val INSTANCE_PATH = "flume.manager.instance.path"
    val INSTANCE_DEF_PATH = s"${System.getProperty("user.home")}/flume_instance"

    // java 安装路径
    val JDK_INSTALL_PATH = "flume.manager.java.path"

    // 管理server url
    val SER_URL = "flume.manager.server.url"
  }

  def propertiesLoad(fileName: String): Properties = {
    val p = new Properties()
    p.load(new FileInputStream(fileName))
    p
  }

  def propertiesToMap(properties: Properties): Map[String, String] =
    properties.entrySet().map(s => s.getKey.toString -> s.getValue.toString).toMap


  def mapToProperties(map: Map[String, String]): Properties = {
    val proper = new Properties()
    proper.putAll(map)
    proper
  }

  case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

  case class FetchInstance(name: String)
  case class FlumeResponse(code: Int, message: String)
  case class Flume(url: String, force: Boolean)
  case class FlumeJar(url: String, name: String)

  case class FlumeStatus(instance: FlumeInstance, var running: Boolean) {
    def setRun(isRunning: Boolean): FlumeStatus = {
      running = isRunning
      this
    }
  }
  case class FlumeInstance(name: String,
                           env: Map[String, String],
                           conf: Map[String, String],
                           log: Map[String, String]) {
    private def _write(m: Map[String, String], fileName: String): Unit = {
      val write = new PrintWriter(new File(fileName))
      m.map(s => s"${s._1}=${s._2}").foreach(write.println)
      write.close()
    }

    def writeEnv(kv: Map[String, String], path: String): Unit =
      _write(env ++ kv, s"$path/$name/conf/flume-env.sh")
    def writeConf(kv: Map[String, String], path: String): Unit =
      _write(conf ++ kv, s"$path/$name/conf/flume-conf.properties")
    def writeLog(kv: Map[String, String], path: String): Unit =
      _write(log ++ kv, s"$path/$name/conf/log4j.properties")

    def storeInstance(path: String): Unit = {
      val n = Map.empty[String, String]
      writeConf(n,path)
      writeEnv(n,path)
      writeLog(n,path)
    }
  }

  case class StartInstance(name: String)
  case class StopInstance(name: String)
  case class StatusInstance(name: String)

  case object FlumeHeartbeat

}
