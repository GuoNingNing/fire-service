package org.fire.service.restful


import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.fire.service.core.Supervisor
import org.fire.service.core.app.AppManager

import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by guoning on 2018/1/25.
  *
  * 启动入口
  */
object FireService {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {

    /*
    * 默认只会加载src/main/resource/application.conf文件
    * 需要指定配置文件路径则如下
    * val config = ConfigFactory.parseFile(new File("/xxx/xxx.conf"))
    * */
    val config = ConfigFactory.load()
    val system = ActorSystem("FireService", config)

    logger.info("FireService starting ...")
    val supervisor = system.actorOf(Props(classOf[Supervisor], config), Supervisor.NAME)

    new RestfullApi(system, config).start()

  }

}
