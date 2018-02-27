package org.fire.service.restful.route

import akka.actor.ActorSystem
import org.fire.service.core.BaseRoute
import org.fire.service.core.ResultJsonSupport._
import spray.http.{MediaTypes, MultipartFormData, StatusCodes}
import spray.routing.Route


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
      } ~ (post & path("upload")) {
      //文件上传示例 curl -F f1=@userinfo.txt http://localhost:9200/hello/upload

      entity(as[MultipartFormData]) { formData =>
        respondWithMediaType(MediaTypes.`application/json`) { ctx =>
          val part = formData.fields.head
          logger.info(s"upload file ${part.name.getOrElse("none")}")
          ctx.complete(StatusCodes.OK, success("you upload file info %s ", new String(part.entity.data.toByteArray)))
        }
      }
    }
  }
}
