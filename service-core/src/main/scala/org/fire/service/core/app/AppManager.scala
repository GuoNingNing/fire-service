package org.fire.service.core.app

import java.io.{File, PrintWriter}

import com.typesafe.config.ConfigFactory
import org.fire.service.core.{BaseActor, app}
import org.fire.service.core.ResultJsonSupport._
import org.fire.service.core.app.AppManager.Submit

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.sys.process.{Process, _}
import spray.json._
import DefaultJsonProtocol._


/**
  * Created by guoning on 2018/1/30.
  *
  * 常驻Actor服务，接收SparkApp注册信息，并监控运行状态
  *
  */
class AppManager extends BaseActor {

  import AppManager._

  private lazy val config = ConfigFactory.load()

  private lazy val appid_r = """application_\d{13}_\d+""".r

  private lazy val submitApps = new mutable.HashMap[String, AppInfo]()


  override def preStart(): Unit = {

    readSubmitAppsFromFile()
    context.system.scheduler.schedule(60 seconds, 5 seconds, self, CheckAppState)
  }

  /**
    * 检查 App状态
    */
  private def checkAppState(): Unit = {
    logger.info(s"check for app timeout ...")
    val currentTime = System.currentTimeMillis()

    val needRestart = submitApps.values.filter(x => x.lastHeartbeat < currentTime - x.period && x.state != AppState.UNKNOWN)

    for (app <- needRestart) {
      logger.warn(s"restart app $app")
      submitApps.remove(app.appId)
      self ! app.submitInfo
    }
  }


  override def receive: PartialFunction[Any, Unit] = {

    case submit: Submit => submitApp(submit)

    case Heartbeat(appId, period) => heartbeat(appId, period)

    case Monitors => sender() ! submitApps.values

    case Kill(appId) => KillApp(appId)

    case CheckAppState => checkAppState()

  }


  /**
    * 心跳检测
    *
    * @param appId
    * @param period
    * @return
    */
  private def heartbeat(appId: String, period: Long) = {
    logger.info(s"receive heartbeat appId $appId ")
    submitApps.get(appId) match {
      case Some(app) =>
        submitApps.put(appId, app.hearbeat(period))
      case None =>
        logger.warn(s"receive heartbeat but not found appId $appId ")
    }
  }



  /**
    * 提交运行一个App
    *
    * @param submit
    */
  private def submitApp(submit: Submit): Unit = {
    val currentSender = sender()

    val process = Process(submit.command, submit.args)

    var appId: String = null

    val future = Future[Int] {
      process ! ProcessLogger(
        stdout => logger.info(stdout), stderr => {
          if (appId == null) {
            appId = (appid_r findFirstIn stderr).orNull
            logger.info(stderr)
          }
        })
    }

    future onSuccess {
      case 0 => //启动成功，加入到监控列表
        logger.info(s"Start-up success $submit")
        submitApps.put(appId, AppInfo(appId, submit))

        writeSubmitAppsToFile()

        currentSender ! s"Start-up success $appId"

      case _ =>
        logger.error(s"Start-up failure $submit")

        currentSender ! "Start-up failure"
    }

    future onFailure {
      case e => logger.error(e.getMessage)
        currentSender ! e.getMessage
    }
  }


  private def KillApp(appId: String): Unit = {
    val currentSender = sender()
    val msg = submitApps.remove(appId) match {
      case Some(appid) =>
        writeSubmitAppsToFile()

        val result = s"yarn application -kill $appId" !!

        logger.info(s"kill appId $appId $result")
        result
      case None => s"the appId $appId is not in the monitors"
    }

    currentSender ! msg
  }

  private def writeSubmitAppsToFile(): Unit = {
    val file = new File("submitApps.json")

    if (!file.exists()) {
      file.createNewFile()
      logger.info(s"create new file $file")
    }

    val writer = new PrintWriter(file)
    for (elem <- submitApps.values) {
      val json = elem.toJson.compactPrint
      writer.println(json)
      logger.info(s"write appinfo $json")
    }

    writer.close()
  }

  private def readSubmitAppsFromFile(): Unit = {
    val file = new File("submitApps.json")
    if (file.exists()) {
      val submitJson = Source.fromFile(file)

      for (elem <- submitJson.getLines()) {
        val appInfo = elem.parseJson.convertTo[AppInfo]
        submitApps.put(appInfo.appId, appInfo)
        logger.info(s"read appinfo $appInfo")
      }
      submitJson.close()
    }
  }

  override def postStop(): Unit = {
    writeSubmitAppsToFile()
    logger.info(s"stop actor [$this]")
  }

}

object AppManager {

  /**
    * 执行 shell
    *
    * @param command
    * @param args
    */
  case class Submit(command: String, args: Seq[String])

  /**
    * 心跳
    *
    * @param appid
    * @param period
    */
  case class Heartbeat(appid: String, period: Long)

  case class Kill(appId: String)

  case object Monitors

  case object CheckAppState

}

/**
  *
  * @param appId
  * @param submitInfo
  * @param period
  * @param lastHeartbeat
  * @param state
  */
case class AppInfo(
                    appId: String,
                    submitInfo: Submit,
                    var period: Long = 0,
                    var lastHeartbeat: Long = 0,
                    var state: String = AppState.UNKNOWN) {

  def hearbeat(_period: Long): AppInfo = {
    lastHeartbeat = System.currentTimeMillis()
    state = AppState.ALIVE
    period = _period
    this
  }

}

object AppState {

  def main(args: Array[String]): Unit = {
    val appInfo = AppInfo("a", Submit("a", Seq("bbb")))
    val appInfo2 = AppInfo("b", Submit("b", Seq("bbb")))

    val submitApps = new mutable.HashMap[String, AppInfo]()

    submitApps.put(appInfo.appId, appInfo)
    submitApps.put(appInfo2.appId, appInfo2)

    def writeSubmitAppsToFile(): Unit = {
      val file = new File("submitApps.json")

      if (!file.exists()) {
        file.createNewFile()
        println(s"create new file $file")
      }

      val writer = new PrintWriter(file)
      for (elem <- submitApps.values) {
        val json = elem.toJson.compactPrint
        writer.println(json)
        println(s"write appinfo $json")
      }

      writer.close()
    }

    writeSubmitAppsToFile()
  }


  val UNKNOWN = "unknown"
  val ALIVE = "alive"
  val KILLED = "killed"
}

