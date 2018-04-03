package org.fire.service.restful.route.naja

import java.text.SimpleDateFormat

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import CollectRouteConstantConfig._

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

/**
  * Created by cloud on 18/3/22.
  */
class MonitorActor(val config: Config) extends Actor with ActorLogging{
  val hostTimeout = config.getInt(HOST_TIMEOUT,HOST_TIMEOUT_DEF)

  var roleHistory: List[RoleRow] = List.empty
  var hostHistory: List[Host] = List.empty

  override def receive: Receive = {
    case ding: Ding =>
//      SendManager.sendDing(ding)
      log.info(s"send ${ding.msg} to Ding")

    case mail: Mail =>
//      SendManager.sendEmail(mail)
      log.info(s"send ${mail.msg} to Mail")

    case weChat: WeChat =>
      log.info(s"weChat: ${weChat.msg}")

    case InitMonitor =>
      val hostList = MonitorManager.checkHost(hostTimeout)
      val roleList = MonitorManager.checkRole(hostTimeout)
      if(hostList.nonEmpty || roleList.nonEmpty) {
        val hostMsg = MonitorManager.hostAlertMsg(hostList, hostHistory)
        val roleMsg = MonitorManager.roleAlertMsg(roleList, roleHistory)
        val sendMsg = MonitorManager.getSendMsg(config, hostMsg + "\n" + roleMsg)
        sendMsg match {
          case Some(sm) => self ! sm
          case None => log.warning(s"create SendMsg failed. WarningMessage:\n$hostMsg\n$roleMsg")
        }
        hostHistory = hostList
        roleHistory = roleList
      }
  }

}

object MonitorActor {
  val NAME = "monitor-fire-service"

  def apply(config: Config): MonitorActor = new MonitorActor(config)
}


object MonitorManager {
  def getDateFormat(format: String) = new SimpleDateFormat(format)

  def nowTimeout(timeout: Long) = System.currentTimeMillis() - timeout

  def checkHost(hostTimeout: Long): List[Host] = {
    DataManager.hostIdMap.filter {
      case (id,host) => host.mem.timestamp < nowTimeout(hostTimeout)
    }.values.toList
  }

  def getSendMsg(config: Config,msg: String): Option[SendMsg] = {
    config.getString("monitor.type","ding") match {
      case "ding" => getDingMsg(config,msg)
      case "mail" => getMailMsg(config,msg)
      case "weChat" => getWeChatMsg(config,msg)
      case _ => Some(WeChat("",Seq.empty[String],msg))
    }
  }

  def getDingMsg(config: Config,msg: String): Option[Ding] = {
    Try {
      val dingPrefix = "monitor.ding"
      val url = config.getString(s"$dingPrefix.url", "")
      val contacts = config.getStringList(s"$dingPrefix.contacts", List(""))
      Ding(url, contacts, msg)
    }match {
      case Success(d) => Some(d)
      case Failure(e) => None
    }
  }
  def getMailMsg(config: Config,msg: String): Option[Mail] = {
    Try {
      val mailPrefix = "monitor.mail"
      val server = config.getString(s"$mailPrefix.server", "smtp.exmail.qq.com")
      val port = config.getInt(s"$mailPrefix.port", 465)
      val user = config.getString(s"$mailPrefix.user")
      val password = config.getString(s"$mailPrefix.password")
      val from = config.getStringList(s"$mailPrefix.from")
      val to = config.getStringList(s"$mailPrefix.to")
      val subject = config.getString(s"$mailPrefix.subject")
      Mail(server, port, user, password, from(0) -> from(1), to, Seq.empty[String],Seq.empty[String],subject, msg)
    }match {
      case Success(m) => Some(m)
      case Failure(e) => None
    }
  }
  def getWeChatMsg(config: Config,msg: String): Option[WeChat] = {
    Try {
      val weChatPrefix = "monitor.weChat"
      val url = config.getString(s"$weChatPrefix.url")
      val contacts = config.getStringList(s"$weChatPrefix.contacts")
      WeChat(url, contacts, msg)
    }match {
      case Success(w) => Some(w)
      case Failure(e) => None
    }
  }

  def checkThreshold(): Unit = {}

  def checkRole(hostTimeout: Long): List[RoleRow] = {
    DataManager.hostIdMap.filter {
      case (id,host) => host.mem.timestamp >= nowTimeout(hostTimeout)
    }.flatMap {
      case (id,host) =>
        host.role.filter(r => r.timestamp < nowTimeout(hostTimeout))
    }.toList
  }

  def roleAlertMsg(roleList: List[RoleRow],roleHistory: List[RoleRow]): String = {
    val dateFormat = getDateFormat("yyyy-MM-dd HH:mm:ss")
    val recoveryRole = roleHistory.filter(d => !roleList.contains(d)).map {
      r => (r.id,DataManager.hostIdMap(r.id).hostName,r.role,dateFormat.format(r.timestamp))
    }
    val deadRole = roleList.map {
      r => (r.id,DataManager.hostIdMap(r.id).hostName,r.role,dateFormat.format(r.timestamp))
    }
    val msg = (r: List[(String,String,String,String)],t: String) => r.isEmpty match {
      case true => ""
      case false =>
        s"""
           |$t
           |HostId\t\tHostName\t\tRoleName\t\tLastUpdate
           |${r.map(d => s"${d._1}\t\t${d._2}\t\t${d._3}\t\t${d._4}").mkString("\n")}
         """.stripMargin
    }
    val deadMsg = msg(deadRole,"dead role list:")
    val recoveryMsg = msg(recoveryRole,"recovery role list:")

    if(deadMsg+recoveryMsg != "")
      s"""
       |$deadMsg
       |
       |$recoveryMsg
     """.stripMargin
    else ""
  }

  def hostAlertMsg(hostList: List[Host],hostHistory: List[Host]): String = {
    val dateFormat = getDateFormat("yyyy-MM-dd HH:mm:ss")
    val recoveryHost = hostHistory.filter(h => !hostList.contains(h)).map {
      h => (h.hostId,h.hostName,dateFormat.format(h.mem.timestamp))
    }
    val deadHost = hostList.map {
      h => (h.hostId,h.hostName,dateFormat.format(h.mem.timestamp))
    }
    val msg = (l: List[(String,String,String)],t: String) => l.isEmpty match {
      case true => ""
      case false =>
        s"""
           |$t
           |HostId\t\tHostName\t\tLastUpdate
           |${l.map(d => s"${d._1}\t\t${d._2}\t\t${d._3}").mkString("\n")}
         """.stripMargin
    }
    val deadMsg = msg(deadHost,"dead host list:")
    val recoveryMsg = msg(recoveryHost,"recovery host list:")
    if(deadMsg+recoveryMsg != "")
      s"""
         |$deadMsg
         |
         |$recoveryMsg
       """.stripMargin
    else ""
  }

}
