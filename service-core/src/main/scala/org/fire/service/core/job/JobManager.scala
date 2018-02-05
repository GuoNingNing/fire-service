package org.fire.service.core.job

import java.io.File

import org.fire.service.core.BaseActor
import org.fire.service.core.ResultJsonSupport._
import spray.json._

import scala.sys.process.Process

/**
  * Created by guoning on 2018/1/30.
  *
  */
class JobManager extends BaseActor {

  import JobManager._

  override def receive = {
    /**
      * 运行一个Spark APP
      */
    case RunApp(command, args, body) =>

      val bodyStr = body.toJson.compactPrint.replaceAll("\\{\\{", "{ {").replaceAll("\\}\\}", "} }")
      val argument = args ++ Seq(bodyStr)
      val process = Process(command, argument)
      process #>> new File("/dev/null") !
  }

  override def postStop(): Unit = {
    log.info(s"stop actor [$this]")
  }

}

object JobManager {

  /**
    * 执行 shell
    *
    * @param command
    * @param args
    */
  case class RunApp(command: String, args: Seq[String], body: Map[String, Any]) {

    override def toString = {
      s"""{"command":"$command","args":${args.map(x => s""""$x"""").mkString("[", ",", "]")},"body":${body.toJson.compactPrint}"""
    }
  }

}
