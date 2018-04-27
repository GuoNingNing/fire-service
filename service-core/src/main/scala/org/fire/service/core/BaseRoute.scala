package org.fire.service.core

/**
  * Created by guoning on 2018/1/25.
  *
  */

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}
import spray.http.{MediaTypes, StatusCode, StatusCodes}
import spray.routing.{ExceptionHandler, HttpService, RejectionHandler, Route}

import scala.language.reflectiveCalls

trait BaseRoute extends HttpService {

  import ResultJsonSupport._

  val logger: Logger = LoggerFactory.getLogger(getClass)

  val pathPrefix: String
  val system: ActorSystem

  implicit val timeout: Timeout = Timeout(5, TimeUnit.MINUTES)

  implicit def actorRefFactory: ActorRefFactory = system

  protected lazy val config: Config = ConfigFactory.load()


  @throws(classOf[Exception])
  protected def preStart(): Unit = ()

  /**
    * 异常处理
    */
  private lazy val exceptionHandler = ExceptionHandler {
    case e =>
      respondWithMediaType(MediaTypes.`application/json`) { ctx =>
        ctx.complete(StatusCodes.BadRequest, failure(e.getMessage))
      }
  }


  /**
    * 连接拒绝处理
    */
  private lazy val rejectionHandler = RejectionHandler {
    case Nil /* secret code for path not found */ =>
      respondWithMediaType(MediaTypes.`application/json`) { ctx =>
        ctx.complete(StatusCodes.NotFound, failure("Oh man, what you are looking for ?"))
      }
  }

  def jsonResponse(statusCode: StatusCode,data: ResultMsg): Route =
    respondWithMediaType(MediaTypes.`application/json`) {
      ctx => ctx.complete(statusCode,data)
    }


  protected def routes(): Route

  def route(): Route = {
    preStart()
    pathPrefix(pathPrefix) {
      handleRejections(rejectionHandler) {
        handleExceptions(exceptionHandler) {
          routes()
        }
      }
    }
  }
}




