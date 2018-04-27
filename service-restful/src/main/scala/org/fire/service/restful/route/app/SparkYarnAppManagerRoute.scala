package org.fire.service.restful.route.app

import akka.actor.ActorSystem
import akka.pattern.ask
import org.fire.service.core.app.SparkYarnAppManager
import org.fire.service.core.app.SparkYarnAppManager._
import org.fire.service.core.app.SparkYarnAppJsonFormat._
import org.fire.service.core.{BaseRoute, Supervisor}
import org.fire.service.core.ResultJsonSupport.{failure, success}
import spray.http.StatusCodes
import spray.routing.Route

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by cloud on 18/4/26.
  * 这是 AppManager 对应的使用appName 管理任务的版本
  * 对应的使用的是 SparkYarnAppManager actor
  */
class SparkYarnAppManagerRoute(override val system: ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "app"
  private lazy val appManager = system.actorSelection(s"/user/${Supervisor.NAME}/${SparkYarnAppManager.NAME}")
  private lazy val runCmd = config.getString("app.manager.submit.command")

  override def routes(): Route = {
    monitor()
      .~(recvHeartbeat())
      .~(submitApp())
      .~(scheduledApp())
      .~(appKill())
  }

  private def appResponse(future: Future[Any]): Route = {
    Try {
      Await.result(future, 30 seconds).asInstanceOf[AppManagerResponse]
    } match {
      case Success(s) => jsonResponse(StatusCodes.OK, success(s.msg))
      case Failure(e) => jsonResponse(StatusCodes.InternalServerError, failure(e.getMessage))
    }
  }

  private def monitor(): Route = {
    (get & path("monitors" / Segment )) { appName =>
      appResponse(appManager ? AppMonitor(appName))
    }
  }

  private def recvHeartbeat(): Route = {
    (get & path("heartbeat" / Segment / Segment / Segment)) { (appName, appId, period) =>
      appResponse(appManager ? AppHeartbeat(appName,appId,period.toInt))
    }
  }

  private def submitApp(): Route = {
    (post & path("submit")) {
      entity(as[App]) { app =>
        appResponse(appManager ? app)
      }
    }
  }

  private def scheduledApp(): Route = {
    (post & path("scheduled")){
      entity(as[AppScheduled]) { appScheduled =>
        appResponse(appManager ? appScheduled)
      }
    }
  }

  private def appKill(): Route = {
    (get & path("kill" / Segment)) { appName =>
      appResponse(appManager ? AppKill(appName))
    }
  }

}
