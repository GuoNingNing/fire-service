package org.fire.service.restful

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import java.io.File

/**
  * Created by guoning on 2018/1/25.
  *
  * 启动入口
  */
object FireService {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    /*
    //用于加载任意位置配置文件
    val config = ConfigFactory.parseFile(new File("/xxx/xxx"))
     */
    //ConfigFactory.load() 只会加载src/resource/application.conf文件
    val config = ConfigFactory.load()
    val system = ActorSystem("FireService", config)

    new RestfullApi(system, config).start()
  }

}
