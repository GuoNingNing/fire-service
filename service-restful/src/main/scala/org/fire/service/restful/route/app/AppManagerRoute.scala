package org.fire.service.restful.route.app

import akka.actor.ActorSystem
import akka.pattern.ask
import org.fire.service.core.BaseRoute
import org.fire.service.core.ResultJsonSupport._
import org.fire.service.core.app.AppManager
import org.fire.service.core.app.AppManager.{Kill, Monitors, Submit}
import spray.http.{MediaTypes, StatusCodes}
import spray.routing.Route
import org.fire.service.core.app.AppInfo

import scala.concurrent.ExecutionContext.Implicits.global


/**
  * App 管理路由
  *
  * @param system
  */
class AppManagerRoute(override val system: ActorSystem) extends BaseRoute {
  override val pathPrefix: String = "app"

  private lazy val appManager = system.actorSelection("/user/Supervisor/AppManager")

  private lazy val deployPath = config.getString("app.manager.path")

  override protected def routes(): Route = {

    //获取监控列表
    (get & path("monitors")) {
      respondWithMediaType(MediaTypes.`application/json`) { ctx =>
        (appManager ? Monitors).map {
          case apps: Iterable[AppInfo] => ctx.complete(StatusCodes.OK, apps)
        }.recover {
          case e: Exception =>
            logger.error(e.getMessage)
            ctx.complete(500, e.getMessage)
        }
      }
    } ~ // 接收心跳
      (get & path("heartbeat" / Segment / Segment)) { (appId, period) =>

        appManager ! AppManager.Heartbeat(appId, period.toLong)

        complete(StatusCodes.OK)

      } ~ // 杀掉一个进程
      (get & path("kill" / Segment)) {
        appId =>
          respondWithMediaType(MediaTypes.`application/json`) { ctx =>

            (appManager ? Kill(appId)).map {
              case msg: String => ctx.complete(StatusCodes.OK, success(msg))
            }.recover {
              case e: Exception =>
                logger.error(e.getMessage)
                ctx.complete(500, e.getMessage)
            }
          }
      } ~ (post & path("submit")) {
      entity(as[Submit]) { submit =>

        respondWithMediaType(MediaTypes.`application/json`) { ctx =>
          appManager ? Submit(s"$deployPath/${submit.command}", submit.args) map {
            case msg: String => ctx.complete(StatusCodes.OK, success(msg))
          } recover {
            case e: Exception =>
              logger.error(e.getMessage)
              ctx.complete(500, failure(e.getMessage))
          }
        }
      }
    }
  }
}
