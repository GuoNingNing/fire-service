package org.fire.service.core

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

/**
  * Created by guoning on 2018/3/8.
  *
  *
  */

trait BaseSupervisor extends BaseActor {
  val logger: Logger = LoggerFactory.getLogger(getClass)
}

class Supervisor extends BaseSupervisor {

  val config = ConfigFactory.load()
  val actorSet = mutable.Set.empty[ActorRef]

  // TODO: 添加ShutDown 释放资源啊等等 ..
  override def receive = {
    case _ =>
  }

  override def preStart(): Unit = {
    logger.info("Supervisor starting ...")
    buildActor()
  }

  /**
    * 从配置文件中读取Actor信息，并实例化
    */
  private def buildActor(): Unit = {
    // TODO: 考虑丰富Actor的配置信息
    for (clazzName <- config.getStringList("spray.can.server.actors").distinct) {
      Try {
        logger.info(s"load actor for clazz $clazzName")
        val clazz = Class.forName(clazzName, true, Thread.currentThread().getContextClassLoader)
        // TODO: 限定，clazz 必须为BaseSupervisor 的子类
        val name = clazzName.split("\\.").last
        context.actorOf(Props(clazz), name = name)
      } match {
        case Success(actor) =>
          logger.info(s"success start actor for clazz $clazzName ")
          actorSet.add(actor)
        case Failure(f) =>
          logger.error(s"failure start actor for clazz $clazzName ${f.getMessage}")
          f.printStackTrace()
      }
    }
  }

}

object Supervisor {
  val NAME = "Supervisor"
}
