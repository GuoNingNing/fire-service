package org.fire.service.restful.actor.naja

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.Config
import org.fire.service.restful.route.naja.CollectRouteConstantConfig._
import org.fire.service.restful.route.naja._
import org.fire.service.restful.util.naja.SendManager

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by cloud on 18/3/22.
  */
class MonitorActor(val config: Config) extends Actor with ActorLogging{

  import MonitorDataStruct._

  implicit private val timeout = Timeout(20, TimeUnit.SECONDS)

  private val hostManagerSelection = context.actorSelection(s"/user/${HostManagerActor.NAME}")

  private val dateFormat = MonitorManager.getDateFormat("yyyy-MM-dd HH:mm:ss")
  private val hostTimeout = config.getInt(HOST_TIMEOUT,HOST_TIMEOUT_DEF)
  private var roleHistory: List[RoleRow] = List.empty
  private var hostHistory: List[Host] = List.empty
  private val alertInterval = 300 // config.getInt("")

  override def preStart(): Unit = {
    val time = Timeout(alertInterval, TimeUnit.SECONDS)

    context.system.scheduler.schedule(time.duration, time.duration, self, MonitorAlert)
  }

  override def receive: Receive = {
    case ding: Ding =>
      SendManager.sendDing(ding)
      log.info(s"send ${ding.msg} to Ding")

    case mail: Mail =>
      SendManager.sendEmail(mail)
      log.info(s"send ${mail.msg} to Mail")

    case weChat: WeChat =>
      log.info(s"weChat: ${weChat.msg}")

    case MonitorAlert => monitorAlert()

  }

  private def monitorAlert(): Unit = {
    hostManagerSelection ? HostRead(None) map(_.asInstanceOf[List[Host]]) onComplete {
      case Success(s) =>
        implicit val hosts = s
        val host = s.filter(h => isTimeout(h.mem.timestamp))
        val role = s.filter(h => !isTimeout(h.mem.timestamp)).flatMap(h => h.role.filter(r => isTimeout(r.timestamp)))
        val msg = roleAlertMsg(role) + "\n" + hostAlerMsg(host)
        if(msg != "\n"){
          val sendMsg = MonitorManager.getSendMsg(config,msg)
          sendMsg match {
            case Some(e) => self ! e
            case None => log.warning("create sendMsg failed. ")
          }
        }
      case Failure(t) => log.warning("check host timeout fetch hosts failed. ",t)
    }
  }

  private def isTimeout(time: Long) = time < System.currentTimeMillis() - hostTimeout

  private def roleAlertMsg(roleList: List[RoleRow])(implicit hosts: List[Host]): String = {
    val recoveryRole = roleHistory.filter(s => !roleList.contains(s))
    val deadRole = roleList.filter(s => !roleHistory.contains(s))
    val hostIdMap = hosts.map(h => h.hostId -> h).toMap
    val msg = (rList: List[RoleRow]) => rList.groupBy(_.id).map { case (id, roles) =>
      val rolesMsg = roles.map {
        r => s"Role: ${r.role}, LastUpdate: ${dateFormat.format(r.timestamp)}"
      }.mkString("\n\t")
      s"Host: ${hostIdMap(id).hostName}\n\t$rolesMsg"
    }.mkString("\n")
    val rMsg = if(recoveryRole.nonEmpty) "recovery role list:\n"+msg(recoveryRole) else ""
    val dMsg = if(deadRole.nonEmpty) "dead role list:\n"+msg(deadRole) else ""
    val alertMsg = rMsg+"\n"+dMsg
    roleHistory = roleHistory.isEmpty match {
      case true => roleList
      case false => roleHistory.diff(recoveryRole) ::: deadRole
    }
    alertMsg match {
      case "\n" => ""
      case _ => alertMsg
    }
  }

  private def hostAlerMsg(hostList: List[Host]): String = {
    val recoveryHost = hostHistory.filter(h => !hostList.contains(h))
    val deadHost = hostList.filter(h => !hostHistory.contains(h))
    val msg = (hList: List[Host]) => hList.map {
      h => s"Host: ${h.hostName}, LastUpdate: ${dateFormat.format(h.mem.timestamp)}"
    }.mkString("\n")
    val rMsg = if(recoveryHost.nonEmpty) "recovery host list:\n"+msg(recoveryHost) else ""
    val dMsg = if(deadHost.nonEmpty) "dead host list:\n"+msg(deadHost) else ""
    val alertMsg = rMsg+"\n"+dMsg
    hostHistory = hostHistory.isEmpty match {
      case true => hostList
      case false => hostHistory.diff(recoveryHost) ::: deadHost
    }
    alertMsg match {
      case "\n" => ""
      case _ => alertMsg
    }
  }
}

object MonitorActor {
  val NAME = "monitor-fire-service"

  def apply(config: Config): MonitorActor = new MonitorActor(config)

  def props(config: Config): Props = Props(apply(config))
}


object MonitorManager {
  def getDateFormat(format: String) = new SimpleDateFormat(format)

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

}

object MonitorDataStruct {
  case object MonitorAlert
}