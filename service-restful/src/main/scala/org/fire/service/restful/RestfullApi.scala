package org.fire.service.restful

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.fire.service.core.BaseRoute
import org.slf4j.{Logger, LoggerFactory}
import spray.routing.{Route, SimpleRoutingApp}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

/**
  * Created by guoning on 2018/1/25.
  *
  */
class RestfullApi(system: ActorSystem, config: Config) extends SimpleRoutingApp {

  val logger: Logger = LoggerFactory.getLogger(getClass)


  def routes(): Route = {

    var indexRoute = (get & path("")) (complete("welcome"))

    buildRoute().foreach(r => {
      logger.info(s"registered route  $r")


      indexRoute = indexRoute.~(r.route())
    })
    indexRoute
  }

  def start(): Unit = {
    lazy val bindAddress = Try(config.getString("service.host")).getOrElse("localhost")
    lazy val port = Try(config.getInt("service.port")).getOrElse(80)

    logger.info("Starting restfull api service...")
    implicit val actorSystem: ActorSystem = system

    startServer(bindAddress, port, "fire-restful-api-server")(routes())
  }

  /**
    * 配置文件获取路由
    *
    * @return
    */
  private def buildRoute() = config.getStringList("spray.can.server.routes").distinct
    .flatMap(clazzName => {
      Try {
        logger.info(s"load route for clazz $clazzName")
        val clazz = Class.forName(clazzName, true, Thread.currentThread().getContextClassLoader)
        val constructors = clazz.getConstructor(classOf[ActorSystem])
        constructors.newInstance(system).asInstanceOf[BaseRoute]
      } match {
        case Success(ok) => Some(ok)
        case Failure(er) =>
          logger.error(er.getMessage)
          None
      }
    })
}

