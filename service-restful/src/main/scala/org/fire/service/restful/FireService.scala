package org.fire.service.restful


import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.fire.service.restful.route.naja.{CollectCacheActor, CollectDBActor, JedisConnect, LoadActor}
import org.slf4j.LoggerFactory
import org.fire.service.core.Supervisor

import scala.slick.driver.MySQLDriver.simple._


/**
  * Created by guoning on 2018/1/25.
  *
  * 启动入口
  */
object FireService {

  private val logger = LoggerFactory.getLogger(getClass)
  val dbActorName = "db-file-service"
  val cacheActorName = "cache-fire-service"
  val loadActorName = "load-fire-service"

  def main(args: Array[String]): Unit = {

    //默认只会加载src/main/resource/application.conf文件
    val config = ConfigFactory.load()
    val system = ActorSystem("FireService", config)
    val db = Database.forConfig("mysql",config.getConfig("db"))
    val jedisConnect = JedisConnect(config.getConfig("redis.connect"))

    system.actorOf(Props(new CollectDBActor(db,config)),dbActorName)
    system.actorOf(Props(new CollectCacheActor(jedisConnect,config)),cacheActorName)
    system.actorOf(Props(new LoadActor(config)),loadActorName)

    logger.info("FireService starting ...")
    val supervisor = system.actorOf(Props[Supervisor], name = "Supervisor")

    new RestfullApi(system, config).start()

  }

}
