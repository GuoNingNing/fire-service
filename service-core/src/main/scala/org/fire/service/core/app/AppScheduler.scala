package org.fire.service.core.app

import java.io.{File, FileNotFoundException, FileOutputStream, PrintWriter}
import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorRef
import org.fire.service.core.app.AppManager.{StartFailure, Submit}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.sys.process.Process
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Created by cloud on 18/4/20.
  */
object AppScheduler {

  private val logger: Logger = LoggerFactory.getLogger(getClass)
  val jobMap = new ConcurrentHashMap[String,ScheduledApp]()
  val scheduledMap = new ConcurrentHashMap[String,Long]()
  val scheduledWaitTime: Long = 3600*24*100
  val checkpointFile = "spark_App_scheduled.checkpoint"


  /**
    * 把提交的信息保存到全局hashMap中
    */
  def registerScheduledApp(submit: Submit,scheduled: Long): Unit = {
    val key = getScheduledKey(submit)
    val value = ScheduledApp(scheduled,submit)
    jobMap += key -> value
    scheduledMap += key -> scheduled
  }

  /**
    * 提交函数 对原提交函数做拦截
    *
    * @param submit  Submit对象
    * @param sender  actorRef对象
    * @param f       原submit函数
    *
    * 通过检查submit.args中是否包含有scheduled参数来决定调用那个submitApp
    */
  def submitApp(submit: Submit,sender: ActorRef)(f: Submit => Unit): Unit = {
    if(!submit.args.contains("scheduled")){
      f(submit)
      return
    }
    var scheduled: Long = 0L
    for (i <- submit.args.indices) {
      if (submit.args(i) == "scheduled") {
        Try {
          submit.args(i + 1).toLong
        } match {
          case Success(l) => scheduled = l
          case Failure(e) =>
            sender ! StartFailure(e.getMessage)
            return
        }
      }
    }

    registerScheduledApp(submit,scheduled)
    flushCheckpoint(checkpointFile,getScheduledKey(submit),scheduled)
    runApp(submit)
  }

  def runApp(submit: Submit): Unit = {
    val process = Process(submit.command,submit.args)
    val res = Future {
      process.run().exitValue()
    }

    val key = getScheduledKey(submit)
    val scheduled = scheduledMap.getOrElse(key,0L)
    scheduledMap += key -> (scheduled + scheduledWaitTime)

    res.onComplete {
      case Success(i) =>
        scheduledMap += key -> (scheduledMap(key) - scheduledWaitTime)
        logger.info(s"${submit.command} ${submit.args.mkString(" ")} scheduled success.")

      case Failure(e) =>
        scheduledMap += key -> (scheduledMap(key) - scheduledWaitTime)
        logger.error(s"${submit.command} ${submit.args.mkString(" ")} scheduled failed. ",e)
    }
  }

  def checkScheduledApp(interval: Long): Unit = {
    scheduledMap.foreach { case (key,scheduled) =>
      if(scheduled < 0){
        runApp(jobMap(key).submit)
      }else{
        scheduledMap += key -> (scheduledMap(key) - interval)
      }
    }
  }

  def flushCheckpoint(fileName: String,key: String,scheduled: Long): Unit = {
    try {
      val file = new File(fileName)
      var printWriter: PrintWriter = null.asInstanceOf[PrintWriter]
      if (file.exists() && file.isFile) {
        printWriter = new PrintWriter(new FileOutputStream(file, true))
      } else {
        printWriter = new PrintWriter(file)
      }
      printWriter.append(key+s"\u0000$scheduled\n")
    } catch {
      case NonFatal(e) =>
        logger.error("flushCheckpoint failed. ",e)
    }
  }

  def recoveryCheckpoint(fileName: String): Unit = {
    try{
      val source = Source.fromFile(new File(fileName))
      source.getLines.map(_.trim.split("\u0000")).filter(_.length == 3).foreach { d =>
        val scheduled = d(2).toLong
        val submit = Submit(d(0),d(1).split("\u0001"))
        jobMap += getScheduledKey(submit) -> ScheduledApp(scheduled,submit)
      }
    } catch {
      case _: FileNotFoundException =>
        logger.info(s"checkpoint file not found. $fileName")
      case NonFatal(e) =>
        logger.info(s"recoveryCheckpoint failed. ",e)
    }
  }

  def getScheduledKey(submit: Submit): String = submit.command+"\u0000"+submit.args.mkString("\u0001")

}

case class ScheduledApp(scheduled: Long,submit: Submit)
