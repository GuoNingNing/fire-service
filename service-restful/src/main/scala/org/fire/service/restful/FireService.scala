package org.fire.service.restful

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * Created by guoning on 2018/1/25.
  *
  * 启动入口
  */
object FireService {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val system = ActorSystem("FireService", config)

    new RestfullApi(system, config).start()
  }

}
