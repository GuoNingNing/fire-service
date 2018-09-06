package org.fire.service.restful.route.naja

import java.util.UUID
import java.io.{ByteArrayInputStream, InputStream, OutputStream}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import org.fire.service.core.{BaseRoute, ResultMsg}
import org.fire.service.core.ResultJsonSupport._
import org.fire.service.core.util.Utils
import org.fire.service.restful.actor.naja.DynamicDeployDataStruct._
import org.fire.service.restful.actor.naja.DynamicDeployJsonFormat._
import org.fire.service.restful.actor.naja._
import org.fire.service.restful.route.naja.CollectRouteConstantConfig._
import org.fire.service.restful.util.naja.YarnAppManager
import spray.http._
import spray.routing.Route

import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by cloud on 18/2/28.
  */
class CollectRoute(override val system : ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "naja"

  private val fileBasePath = config.getString(FILE_SER_PATH,FILE_SER_PATH_DEF)
  private val hostTimeout = config.getInt(HOST_TIMEOUT,HOST_TIMEOUT_DEF)
  private var dbRef: ActorRef = _
  private var cacheRef: ActorRef = _
  private var hostManagerRef: ActorRef = _
  private var monitorRef: ActorRef = _
  private var deployRef: ActorRef = _

  import DataFormat._
  import spray.json._

  override protected def preStart(): Unit = {
    actorInit()
  }

  override protected def routes() : Route = {
    fileManager()
      .~(hostManager())
      .~(monitor())
      .~(sendMessage())
      .~(deployManager())
  }

  private def actorInit(): Unit = {
    // 数据库管理actor
    dbRef = system.actorOf(CollectDBActor.props(config),CollectDBActor.NAME)

    // 缓存管理actor
    cacheRef = system.actorOf(CacheActor.props(config),CacheActor.NAME)

    // 主机管理actor
    hostManagerRef = system.actorOf(HostManagerActor.props(config),HostManagerActor.NAME)

    // 监控管理actor
    monitorRef = system.actorOf(MonitorActor.props(config),MonitorActor.NAME)

    // 部署管理actor
    deployRef = system.actorOf(DynamicDeploy.props(config),DynamicDeploy.NAME)
  }

  private def fileManager(): Route = {
    (get & path("source" / RestPath)) { path =>
      getFromFile(fileBasePath + "/" + path.toString())
    } ~ (post & path("upload")) {
      entity(as[MultipartFormData]) { formData =>
        Try {
          formData.fields.map {
            case BodyPart(entity, headers) =>
              val content = new ByteArrayInputStream(entity.data.toByteArray)
              val fileName = headers.find(_.is("content-disposition"))
                .getOrElse(HttpHeaders.`Content-Disposition`("", Map("filename" -> "tmp_file")))
                .value.split("filename=").last
              val saveBoolean = Utils.save(s"$fileBasePath/$fileName", content)
              (saveBoolean, fileName)
            case _ => (false, "")
          }.head
        } match {
          case Success(res) => successJson(s"$res")
          case Failure(e) => failureJson(e.getMessage)
        }
      }
    }
  }

  private def hostManager(): Route = {
    path("host"){
      post {
        entity(as[Host]) { body =>
          hostManagerRef ! body
          successJson(body.hostId)
        }
      } ~ get{
        parameters('id,'hostName,'timestamp.as[Long]).as(HostRow) {hostRow =>
          val hostList =
            await(hostManagerRef ? HostRead(Some(hostRow)) map(s => s.asInstanceOf[List[Host]]))(List.empty[Host])
          successJson(hostList.toJson.compactPrint)
        }
      }
    } ~ (get & path("hosts")) {
      def hostNameList = await(hostManagerRef ? HostRead(None) map(s => s.asInstanceOf[List[Host]]))(List.empty[Host])
      respondWithMediaType(MediaTypes.`application/json`) { ctx =>
        ctx.complete(StatusCodes.OK, success(hostNameList.toJson.compactPrint))
      }
    }
  }

  private def monitor(): Route = pathPrefix("monitor") {
    path("host") {
      get {
        parameters('id, 'hostName, 'timestamp.as[Long]).as(HostRow) { hr =>
          successJson(s"Interface temporarily unavailable ${hr.id}")
        }
      }
    } ~ path("memory") {
      get {
        parameters('id, 'hostName, 'timestamp.as[Long]).as(HostRow) { hr =>
          successJson(s"Interface temporarily unavailable ${hr.id}")
        }
      }
    }
  }

  private def sendMessage(): Route = pathPrefix("send") {
    (post & path("ding")) {
      entity(as[Ding]) { ding =>
        monitorRef ! ding
        successJson("send ding successful")
      }
    } ~ (post & path("mail")) {
      entity(as[Mail]) { mail =>
        monitorRef ! mail
        successJson("send email successful")
      }
    } ~ (post & path("wechat")) {
      entity(as[WeChat]) { weChat =>
        monitorRef ! weChat
        successJson("send weChat successful")
      }
    }
  }

  private def appManager(): Route = pathPrefix("yarn") {
    (post & path("app")) {
      entity(as[HostJob]) { hostJob =>
        YarnAppManager.hostAppMap += hostJob.hostId -> hostJob.hostApp
        YarnAppManager.hostContainerMap += hostJob.hostId -> hostJob.hostContainer
        hostJob.hostApp.foreach(sparkApp => YarnAppManager.appIdMap += sparkApp.appId -> sparkApp)
        hostJob.hostContainer.foreach {
          container =>
            val containers = YarnAppManager.appContainerMap.getOrDefault(container.appId, List.empty[Container])
            val updateContainers = container :: containers.filter(_.containerId != container.containerId)
            YarnAppManager.appContainerMap += container.appId -> updateContainers
        }
        successJson("successful")
      }
    }
  }

  private def deployManager(): Route = pathPrefix("deploy") {
    path("ready") {
      post {
        entity(as[SubmitDeployJob]) { submitJob =>
          val size = await(deployRef ? submitJob map(_.asInstanceOf[Long]))(0L)
          successJson(size)
        }
      } ~ get {
        parameters('host) { host =>
          val getJob = GetHostReadyDeployJob(host)
          val jobList = await(deployRef ? getJob map(_.asInstanceOf[List[DeployJob]]))(List.empty[DeployJob])
          successJson(jobList.toJson.compactPrint)
        }
      }
    } ~ (get & path("status")) {
      parameters('id) { id =>
        val getJobStatus = GetDeployJobStatus(id)
        val jobStatus = await(deployRef ? getJobStatus map(_.asInstanceOf[DeployJobStatus]))()
        if (null != jobStatus) {
          successJson(jobStatus.toJson.compactPrint)
        } else {
          failureJson(s"job $id not found.")
        }
      }
    } ~ (post & path("complete")) {
      entity(as[CompleteDeployJob]) { completeJob =>
        deployRef ! completeJob
        successJson("success")
      }
    }
  }

  private def successJson(data: Any): Route = jsonResponse(StatusCodes.OK,success(data))
  private def failureJson(data: String): Route = jsonResponse(StatusCodes.OK,failure(data))
  private def errorJson(statusCode: StatusCode,data: String): Route = jsonResponse(statusCode,failure(data))

  private def await[R: ClassTag](future: Future[R], timeout: Int = 30)
                                (default: => R = null.asInstanceOf[R]): R = {
    Try {
      Await.result(future, timeout seconds)
    }.getOrElse(default)
  }

}


