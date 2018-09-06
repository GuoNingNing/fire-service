package org.fire.service.restful.route.flume

import akka.actor.ActorSystem
import akka.pattern.ask
import org.fire.service.core.{BaseRoute, ResultMsg, Supervisor}
import org.fire.service.restful.actor.flume.FlumeManager._
import org.fire.service.restful.actor.flume.FlumeManagerJsonFormat._
import spray.http.{MediaTypes, StatusCode, StatusCodes}
import spray.routing.Route

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by cloud on 18/9/6.
  */
class FlumeManagerRoute(override val system: ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "flume"
  private val flumeManager = system.actorSelection(s"/user/${Supervisor.NAME}/$NAME")


  override def routes(): Route = {
    install()
      .~(jar())
      .~(instance())
      .~(fetch())
      .~(action())
  }

  private def response(code: StatusCode, data: FlumeResponse): Route =
    respondWithMediaType(MediaTypes.`application/json`) { ctx =>
      ctx.complete(code, data)
    }

  private def flumeResponse(future: Future[Any]): Route = {
    Try {
      Await.result(future,timeout.duration).asInstanceOf[FlumeResponse]
    } match {
      case Success(s) => response(StatusCodes.OK, s)
      case Failure(e) => response(StatusCodes.InternalServerError, FlumeResponse(-1, e.getMessage))
    }
  }

  private def install(): Route = {
    (post & path("install")) {
      entity(as[Flume]) { flume => flumeResponse(flumeManager ? flume)}
    }
  }

  private def jar(): Route = {
    (post & path("jar")) {
      entity(as[FlumeJar]) { fJar => flumeResponse(flumeManager ? fJar)}
    }
  }

  private def instance(): Route = {
    (post & path("instance")) {
      entity(as[FlumeInstance]) { instance => flumeResponse(flumeManager ? instance)}
    }
  }

  private def fetch(): Route = {
    (get & path("fetch")) {
      parameters('name) { name => flumeResponse(flumeManager ? FetchInstance(name))}
    }
  }

  private def action(): Route = {
    (get & path("action")) {
      parameters('name, 'action) { (name, cmd) =>
        cmd match {
          case "start" => flumeResponse(flumeManager ? StartInstance(name))
          case "stop" => flumeResponse(flumeManager ? StopInstance(name))
          case _ => flumeResponse(flumeManager ? StatusInstance(name))
        }
      }
    }
  }

}
