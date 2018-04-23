package org.fire.service.core.app

import java.io.{File, PrintWriter}
import java.util.Properties
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.fire.service.core.BaseActor
import org.fire.service.core.ResultJsonSupport._
import org.fire.service.core.app.AppManager.Submit
import spray.json._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.sys.process.{Process, _}


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
  private lazy val appidToAppInfo = new ConcurrentHashMap[String, AppInfo]()
  private lazy val submitToAppid = new ConcurrentHashMap[String, String]()


  override def preStart(): Unit = {

    /**
      * 添加调度信息从文件中恢复
      */
    //    AppScheduler.recoveryCheckpoint(AppScheduler.checkpointFile)
    readAppidToAppInfo()
    context.system.scheduler.schedule(60 seconds, 5 seconds, self, CheckAppState)
  }


  override def receive: PartialFunction[Any, Unit] = {

    /**
      * 添加submit程序,对原submit进行兼容
      */
    case submit: Submit => AppScheduler.submitApp(submit, sender())(submitApp)

    case Heartbeat(appId, period) => heartbeat(appId, period)

    case Monitors => sender() ! appidToAppInfo.values.toList

    case Kill(appId) => KillApp(appId)

    case CheckAppState => checkAndRestart()

  }

  /**
    * 是否需要重启
    *
    * @param x
    * @return
    */
  def isNeedRestart(x: AppInfo): Boolean = {
    x.state match {
      //   提交后一份粥还未收到心跳，则认为启动时报，尝试重启
      case AppState.SUBMITED if x.lastStarttime < System.currentTimeMillis() - 60 * 1000 => true
      //   App 成功运行后，超过指定周期，没有收到心跳，则认为APP运行出错，尝试重启
      case AppState.RUNNING if x.lastHeartbeat < System.currentTimeMillis() - x.period =>
        logger.warn(s"The heartbeat timeout  ${System.currentTimeMillis() - x.lastHeartbeat} lastHeartbeat ${x.lastHeartbeat} period ${x.period}")
        true
      //   通过接口杀死,多次重启失败的APP 不再重启
      case _ => false

    }
  }

  /**
    * 检查并重启APP
    */
  private def checkAndRestart(): Unit = {
    appidToAppInfo.values.filter(isNeedRestart).foreach { app =>
      logger.warn(s"restart app $app")
      restart(appidToAppInfo.remove(app.appId))
    }

    /**
      * 添加调度程序检查是否需要被调度
      * 参数是定时调度的时间间隔
      */
    AppScheduler.checkScheduledApp(5)
  }

  /**
    * 重启
    *
    * @param appInfo
    */
  private def restart(appInfo: AppInfo): Unit = {
    implicit val timeout: Timeout = Timeout(1, TimeUnit.MINUTES)

    Future {
      (self ? appInfo.submitInfo).map {
        case StartSuccess(appid) =>
          logger.info(s"restart success $appid")
        case StartFailure => restartFailure()
      }
    }.recover {
      case e: Exception =>
        logger.error(e.getMessage)
        restartFailure()
    }

    def restartFailure() = {
      if (appInfo.restartCount < 3) {
        logger.warn(s"restart failure $appInfo")
        appidToAppInfo.put(appInfo.appId, appInfo.restart())
      } else {
        // TODO: 连续三次重启失败，则停止重启,加入告警机制
        appidToAppInfo.put(appInfo.appId, appInfo.copy(state = AppState.FAILED))
        logger.error(s"restart failure ${appInfo.restartCount} times")
      }
    }
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
    appidToAppInfo.get(appId) match {
      case app: AppInfo =>
        appidToAppInfo.put(appId, app.hearbeat(period))
      case null =>
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
        stdout => {
          if (appId == null) {
            appId = (appid_r findFirstIn stdout).orNull
          }
          logger.info(stdout)
        }, stderr => {
          if (appId == null) {
            appId = (appid_r findFirstIn stderr).orNull
          }
          logger.info(stderr)
        })
    }

    future onSuccess {
      case msg =>
        if (appId != null) {
          logger.info(s"Start-up success $appId $submit")

          if (appidToAppInfo.containsKey(appId)) {
            currentSender ! StartSuccess(s"the $appId is running don't repeat submit")
          } else {
            //启动成功，加入到监控列表
            appidToAppInfo.put(appId, AppInfo(appId, submit))
            currentSender ! StartSuccess(s"success submit $appId")
          }
          storeAppidToAppInfo()
        } else {
          currentSender ! StartFailure(msg.toString)
        }
    }

    future onFailure {
      case e => logger.error(e.getMessage)
        currentSender ! StartFailure(e.getMessage)
    }
  }


  /**
    * 通过接口杀掉一个APP 并移除监控列表
    *
    * @param appId
    */
  private def KillApp(appId: String): Unit = {
    val currentSender = sender()
    val msg = appidToAppInfo.remove(appId) match {
      case appid: AppInfo =>
        storeAppidToAppInfo()

        val result = s"yarn application -kill $appId" !!

        logger.info(s"kill appId $appId $result")
        result
      case null => s"the appId $appId is not in the monitors"
    }

    currentSender ! msg
  }

  /**
    * 监控信息写入文件
    */
  private def storeAppidToAppInfo(): Unit = {
    val file = new File("appidToAppInfo.json")

    if (!file.exists()) {
      file.createNewFile()
      logger.info(s"create new file $file")
    }

    val writer = new PrintWriter(file)
    for (elem <- appidToAppInfo.values) {
      val json = elem.toJson.compactPrint
      writer.println(json)
      logger.info(s"write appinfo $json")
    }

    writer.close()
  }

  /**
    * 从文件中加载监控信息
    */
  private def readAppidToAppInfo(): Unit = {
    val file = new File("appidToAppInfo.json")
    if (file.exists()) {
      val submitJson = Source.fromFile(file)

      for (elem <- submitJson.getLines()) {
        val appInfo = elem.parseJson.convertTo[AppInfo]
        appidToAppInfo.put(appInfo.appId, appInfo)
        logger.info(s"read appinfo $appInfo")
      }
      submitJson.close()
    }
  }

  override def postStop(): Unit = {
    storeAppidToAppInfo()
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
  case class Submit(command: String, args: Seq[String]) {
    override def toString: String = {
      s"$command ${args.mkString(" ")}"
    }
  }

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

  case class StartSuccess(appId: String)

  case class StartFailure(msg: String)

}

/**
  *
  * @param appId         applicationId
  * @param submitInfo    提交信息
  * @param period        心跳周期
  * @param lastHeartbeat 最后心跳时间
  * @param state         状态
  */
case class AppInfo(
                    appId: String,
                    submitInfo: Submit,
                    var period: Long = 0,
                    var lastHeartbeat: Long = 0,
                    var restartCount: Int = 0,
                    var lastStarttime: Long = System.currentTimeMillis(),
                    var state: String = AppState.SUBMITED) {

  def hearbeat(_period: Long): AppInfo = {
    lastHeartbeat = System.currentTimeMillis()
    state = AppState.RUNNING
    period = _period
    this
  }

  def restart(): AppInfo = {
    restartCount = restartCount + 1
    lastStarttime = System.currentTimeMillis()
    this
  }
}

object AppState {
  val SUBMITED = "submited"
  val RUNNING = "running"
  val FAILED = "failed"
  val KILLED = "killed"

  def main(args: Array[String]): Unit = {
    "running" match {
      case AppState.RUNNING => println("111")
    }
  }

}

