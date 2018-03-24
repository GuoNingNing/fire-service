package org.fire.service.restful.route.naja

import java.text.SimpleDateFormat

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.typesafe.config.Config
import CollectRouteConstantConfig._

import scala.collection.JavaConversions._

/**
  * Created by cloud on 18/3/22.
  */
class MonitorActor(val config: Config) extends Actor with ActorLogging{
  val hostTimeout = config.getLong(HOST_TIMEOUT,HOST_TIMEOUT_DEF)

  var roleHistory: List[RoleRow] = List.empty
  var hostHistory: List[Host] = List.empty

  override def receive: Receive = {
    case ding: Ding =>
      SendManager.sendDing(ding)
      log.info(s"send ${ding.msg} to Ding")

    case mail: Mail =>
      SendManager.sendEmail(mail)
      log.info(s"send ${mail.msg} to Mail")

    case weChat: WeChat =>
      log.info(s"weChat: ${weChat.msg}")

    case InitMonitor =>
      val hostList = MonitorManager.checkHost(hostTimeout)
      val roleList = MonitorManager.checkRole(hostTimeout)
      val hostMsg = MonitorManager.hostAlertMsg(hostList,hostHistory)
      val roleMsg = MonitorManager.roleAlertMsg(roleList,roleHistory)
      val dingMsg = Ding("","",hostMsg+"\n"+roleMsg)
      self ! dingMsg
      hostHistory = hostList
      roleHistory = roleList
  }

}

object MonitorManager {
  def getDateFormat(format: String) = new SimpleDateFormat(format)

  def nowTimeout(timeout: Long) = System.currentTimeMillis() - timeout

  def checkHost(hostTimeout: Long): List[Host] = {
    DataManager.hostIdMap.filter {
      case (id,host) => host.mem.timestamp < nowTimeout(hostTimeout)
    }.values.toList
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
