package org.fire.service.core.app

import java.io.{BufferedInputStream, File, FileInputStream, PrintWriter}
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

import spray.json._
import SparkYarnAppJsonFormat._
import com.typesafe.config.ConfigFactory
import org.apache.hadoop.yarn.client.api.YarnClient
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.conf.Configuration
import org.fire.service.core.BaseActor

import scala.util.{Failure, Success, Try}
import scala.sys.process.Process
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.control.NonFatal

/**
  * Created by cloud on 18/4/23.
  */
class SparkYarnAppManager extends BaseActor{

  import SparkYarnAppManager._
  import SparkYarnAppEnumeration.AppType._
  import SparkYarnAppEnumeration.AppStateType._

  private lazy val conf = ConfigFactory.load()
  private val appMap = new ConcurrentHashMap[String,AppStatus]()
  private lazy val submitWaitTime = conf.getLong("app.manager.submit.wait.timeout")
  private lazy val checkAppInterval = conf.getInt("app.manager.check.app.interval")
  private lazy val checkpointFile = conf.getString("app.manager.checkpoint.file")
  private lazy val yarnConfigFile = conf.getString("app.manager.yarn.conf.file")
  private lazy val cmdPath = conf.getString("app.manager.path")
  private var hadoopConf: Configuration = _
  private var yarnConf: YarnConfiguration = _
  private var yarnClient: YarnClient = _

  override def preStart(): Unit = {
    recoveryCheckpoint()
    context.system.scheduler.schedule(60 seconds, checkAppInterval seconds, self, AppCheck)
  }

  override def receive: Receive = {

    case app: App => submitApp(app)

    case appScheduled: AppScheduled => scheduledApp(appScheduled)

    case appHeartbeat: AppHeartbeat => recvHeartbeat(appHeartbeat)

    case appMonitor: AppMonitor => sender() ! getAppInfo(appMonitor)

    case appKill: AppKill => cancelMonitor(appKill)

    case AppCheck => checkApp()

  }

  override def postStop(): Unit = {
    if(yarnClient != null) {
      yarnClient.close()
    }
  }

  private def startYarnClient(): Unit = {
    hadoopConf = new Configuration()
    hadoopConf.addResource(yarnConfigFile)
    yarnConf = new YarnConfiguration(hadoopConf)
    yarnClient = YarnClient.createYarnClient()
    yarnClient.init(yarnConf)
    yarnClient.start()
  }

  private def getAppName(conf: String): String = Try {
    val prop = new Properties()
    prop.load(new BufferedInputStream(new FileInputStream(conf)))
    prop.getProperty("spark.app.name",prop.getProperty("spark.run.main")+".App")
  }.getOrElse("")

  private def createAppStatus(appName: String,
                              conf: String,
                              appType: AppType,
                              period: Int = 0): AppStatus = {
    appType match {
      case MONITOR => AppStatus(appName,conf,period = period)
      case _ => AppStatus(appName,conf,appType = SCHEDULED,period = period)
    }
  }

  private def recvApp(conf: String,appType: AppType,period: Int = 0): Unit = {
    val appName = getAppName(conf)

    if(appName == ""){
      logger.warn(s"get appName failed. config $conf")
      sender() ! failureRes("parameter error.")
    }else{
      if(!appMap.containsKey(appName)) {
        appMap += appName -> createAppStatus(appName,conf,appType)
        runApp(appName)
        sender() ! successRes(s"success submit $appName.")
      }else{
        sender() ! failureRes(s"app $appName already exists.")
      }
    }
  }

  private def submitApp(app: App): Unit = {
    recvApp(app.conf,MONITOR)
  }

  private def scheduledApp(appScheduled: AppScheduled): Unit = {
    recvApp(appScheduled.conf,SCHEDULED,appScheduled.interval)
  }

  private def runApp(appName: String): Unit = {
    val app = appMap(appName)
    val process = Process(s"$cmdPath/run.sh",List(app.conf))
    val future = Future {
      process.run().exitValue()
    }

    future.onComplete {
      case Success(c) =>
        app.lastStartTime = System.currentTimeMillis()
        logger.info(s"runApp $appName success. exitCode $c")
      case Failure(e) =>
        app.lastStartTime = System.currentTimeMillis()
        logger.error(s"runApp $appName failed. ",e)
    }
  }

  private def recvHeartbeat(appHeartbeat: AppHeartbeat): Unit = {
    logger.info(s"receive heartbeat ${appHeartbeat.appName} ${appHeartbeat.period}")
    appMap.get(appHeartbeat.appName) match {
      case appStatus: AppStatus =>
        appStatus.appId = appHeartbeat.appId
        appMap += appHeartbeat.appName -> appStatus.heartbeat(appHeartbeat.period)
      case null =>
        logger.warn(s"receive heartbeat but not found app ${appHeartbeat.appName}")
    }
    sender() ! successRes()
  }

  private def isNeedRestart(appStatus: AppStatus): Boolean = {
    appStatus.appType match {
      case MONITOR =>
        appStatus.state match {
          case SUBMIT if appStatus.lastStartTime < System.currentTimeMillis() - submitWaitTime =>
            logger.warn(s"The submit wait timeout, last startTime ${appStatus.lastStartTime}.")
            true
          case RUNNING if appStatus.lastHeartbeat < System.currentTimeMillis() - appStatus.period =>
            logger.warn(s"The heartbeat timeout,last heartbeat ${appStatus.lastHeartbeat}.")
            true
          case _ => false
        }
      case _ => false
    }
  }

  private def isNeedScheduled(appStatus: AppStatus): Boolean = {
    appStatus.appType match {
      case SCHEDULED => appStatus.period*1000 < System.currentTimeMillis() - appStatus.lastStartTime
      case _ => false
    }
  }

  private def checkApp(): Unit = {
    appMap.values().filter(isNeedRestart).foreach { app =>
      app.restart()
      logger.warn(s"restart app ${app.appName}")
      runApp(app.appName)
    }
    appMap.values().filter(isNeedScheduled).foreach { app =>
      logger.info(s"scheduler ${app.appName}")
      runApp(app.appName)
    }
    flushCheckpoint()
  }

  private def getAppInfo(appMonitor: AppMonitor): AppManagerResponse = {
    appMap.get(appMonitor.appName) match {
      case app: AppStatus => successRes(app.toJson.compactPrint)
      case null => successRes(appMap.keys().mkString("[\"","\",\"","\"]"))
    }
  }

  private def cancelMonitor(appKill: AppKill): Unit = {
    if(appMap.contains(appKill.appName)){
      appMap -= appKill.appName
      sender() ! successRes(s"${appKill.appName} cancel success.")
    }
    sender() ! failureRes(s"${appKill.appName} not found.")
  }

  private def flushCheckpoint(): Unit = {
    try{
      val printWriter = new PrintWriter(new File(checkpointFile))
      appMap.foreach { case (appName, appStatus) =>
        printWriter.println(appStatus.toJson.compactPrint)
      }
      printWriter.close()
      logger.info("flushCheckpoint success.")
    } catch {
      case NonFatal(e) => logger.warn("flushCheckpoint failed. ",e)
    }
  }

  private def recoveryCheckpoint(): Unit = {
    try{
      val file = new File(checkpointFile)
      if(file.exists()){
        val source = Source.fromFile(file)
        source.getLines.map(_.trim).foreach {
          line =>
            val app = line.parseJson.convertTo[AppStatus]
            appMap += app.appName -> app
        }
        source.close()
        logger.info(s"from $checkpointFile recoveryCheckpoint success.")
      }
    } catch {
      case NonFatal(e) => logger.warn(s"from $checkpointFile recovery failed. ",e)
    }
  }

}

object SparkYarnAppManager {
  val NAME = "SparkYarnAppManager"

  import SparkYarnAppEnumeration.AppType._
  import SparkYarnAppEnumeration.AppStateType._

  case class App(conf: String)
  case class AppScheduled(conf: String, interval: Int)
  case class AppHeartbeat(appName: String,appId: String,period: Int)
  case class AppStatus(appName: String,
                       conf: String,
                       var appType: AppType = MONITOR,
                       var appId: String = "",
                       var period: Int = 0,
                       var lastHeartbeat: Long = 0,
                       var lastStartTime: Long = System.currentTimeMillis(),
                       var restartCount: Int = 0,
                       var state: AppStateType = SUBMIT) {
    def heartbeat(period: Int): AppStatus = {
      lastHeartbeat = System.currentTimeMillis()
      state = RUNNING
      this.period = period
      this
    }

    def restart(): AppStatus = {
      restartCount += 1
      state = RESTART
      appId = ""
      lastStartTime = System.currentTimeMillis()
      this
    }
  }
  case class AppManagerResponse(code: Int,msg: String)
  case class AppMonitor(appName: String)
  case class AppKill(appName: String)
  case object AppCheck

  def failureRes(msg: String = "failed") = AppManagerResponse(-1,msg)
  def successRes(msg: String = "success") = AppManagerResponse(0,msg)


}

object SparkYarnAppEnumeration {
  object AppType extends Enumeration {
    type AppType = Value
    val MONITOR = Value("monitor")
    val SCHEDULED = Value("scheduled")
  }

  object AppStateType extends Enumeration {
    type AppStateType = Value
    val SUBMIT = Value("submit")
    val RUNNING = Value("running")
    val RESTART = Value("restart")
    val FAILED = Value("failed")
    val KILLED = Value("killed")
  }
}


