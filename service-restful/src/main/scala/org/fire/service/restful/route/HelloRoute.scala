package org.fire.service.restful.route

import akka.actor.ActorSystem
import org.fire.service.core.BaseRoute
import org.fire.service.core.ResultJsonSupport._
import spray.http.{MediaTypes, StatusCodes}
import spray.routing.Route
import akka.event.slf4j.Slf4jLogger


/**
  * @param system
  */
class HelloRoute(override val system: ActorSystem) extends BaseRoute {


  override val pathPrefix: String = "hello"

  override protected def routes(): Route = {

    (get & path("world")) {
      complete {
        <h1>This is an GET method</h1>
      }
    } ~
      (post & path("world")) {
        entity(as[String]) { body =>
          respondWithMediaType(MediaTypes.`application/json`) { ctx =>
            ctx.complete(StatusCodes.OK, success(s"you post $body"))
          }
        }
      }
  }
}
