package org.fire.service.restful.route.naja

import org.apache.commons.mail._

import scala.util.{Failure, Success, Try}
import scalaj.http._

/**
  * Created by cloud on 18/3/23.
  */
object SendManager {
  def send(sendMsg: SendMsg): Boolean = sendMsg match {
    case ding: Ding =>
      Try {
        sendDing(ding)
      }match {
        case Success(r) => if(r==200) true else false
        case Failure(e) => false
      }

    case mail: Mail =>
      Try{
        sendEmail(mail)
      }match {
        case Success(r) => true
        case Failure(e) => false
      }

    case weChat: WeChat =>
      Try{
        sendWeChat(weChat)
      }match {
        case Success(r) => true
        case Failure(e) => false
      }
  }

  def sendDing(ding: Ding): Int = {
    val dingMsg =
      s"""
         |{
         |  "msgtype": "text",
         |  "text": {
         |    "content": "${ding.msg}"
         |  },
         |  "at": {
         |    "atMobiles": [
         |      ${ding.contacts.map(d => s""""$d"""").mkString(",")}
         |    ],
         |    "isAtAll": false
         |  }
         |}
        """.stripMargin

    val req = Http(ding.url).postData(dingMsg).header("content-type","application/json")
    val res = req.asString
    res.code
  }

  def sendEmail(mail: Mail): String = {
    val email = new HtmlEmail().setMsg(mail.msg)

    email.setHostName(mail.server)
    email.setSmtpPort(mail.port)
    email.setStartTLSEnabled(true)
    email.setSSLOnConnect(true)
    email.setAuthentication(mail.user,mail.password)

    mail.to.foreach(email.addTo)
    mail.cc.foreach(email.addCc)
    mail.bcc.foreach(email.addBcc)

    email
      .setFrom(mail.from._1,mail.from._2)
      .setSubject(mail.subject)
      .send()
  }

  def sendWeChat(weChat: WeChat): String = {
    s"weChat msg: ${weChat.msg}"
  }
}
