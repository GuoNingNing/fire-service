package org.fire.service.restful.actor.naja

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.{HashMap => JHashMap}

import com.typesafe.config.Config
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json._

import scala.util.{Failure, Success}
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by cloud on 18/5/31.
  */
object DynamicDeployJsonFormat extends DefaultJsonProtocol with SprayJsonSupport {
  import DynamicDeployDataStruct._

  implicit val deployJobFormat = jsonFormat4(DeployJob)
  implicit val getJobStatusFormat = jsonFormat1(GetDeployJobStatus)
  implicit val submitJobFormat = jsonFormat2(SubmitDeployJob)
  implicit val completeJobFormat = jsonFormat5(CompleteDeployJob)
  implicit val jobStatusFormat = jsonFormat2(DeployJobStatus)
}

class DynamicDeploy(val config: Config) extends Actor with ActorLogging{

  import DynamicDeployJsonFormat._
  import DynamicDeployDataStruct._
  import CacheDataStruct._

  implicit private val timeout = Timeout(20, TimeUnit.SECONDS)

  private val cacheSelection = context.actorSelection(s"/user/${CacheActor.NAME}")

  private val allReadyJobMap = new ConcurrentHashMap[String, SubmitDeployJob]()
  private val allCompleteJobKey = "dynamic_deploy_all_complete_job_hash"
  private val allReadyJobKey = "dynamic_deploy_all_ready_job_hash"
  private val jobReadyKey = "dynamic_deploy_ready_job_hash"
  private val jobCompletedKey = "dynamic_deploy_complete_job_hash"

  override def preStart(): Unit = {
    cacheSelection ? RHGetAll(allReadyJobKey) map(_.asInstanceOf[JHashMap[String,String]]) onSuccess {
      case map => map.foreach(s => allReadyJobMap.put(s._1,s._2.parseJson.convertTo[SubmitDeployJob]))
    }
  }

  override def receive: Receive = {
    case submitJob: SubmitDeployJob => submitDeployJob(submitJob)

    case getJobStatus: GetDeployJobStatus => fetchJobStatus(getJobStatus)

    case getJob: GetHostReadyDeployJob => fetchHostAllReadyJob(getJob)

    case complete: CompleteDeployJob => completeJob(complete)
  }

  private def submitDeployJob(submitJob: SubmitDeployJob): Unit = {
    allReadyJobMap.containsKey(submitJob.deployJob.id) match {
      case false =>
        submitJob.hosts.foreach { host =>
          cacheSelection ! RHSet(s"${jobReadyKey}_$host", submitJob.deployJob.id,
            submitJob.deployJob.toJson.compactPrint)
        }
        cacheSelection ! RHSet(allReadyJobKey, submitJob.deployJob.id, submitJob.toJson.compactPrint)
        allReadyJobMap.put(submitJob.deployJob.id, submitJob)
        sender ! submitJob.hosts.size
      case true =>
        log.warning(s"job ${submitJob.deployJob.id} already submit")
        sender ! 0L
    }
  }

  private def fetchJobStatus(job: GetDeployJobStatus): Unit = {
    allReadyJobMap.containsKey(job.id) match {
      case true =>
        cacheSelection ? RHGetAll(s"${jobCompletedKey}_${job.id}") map {
          _.asInstanceOf[JHashMap[String, String]]
        } filter(_.nonEmpty) onComplete {
          case Success(e) =>
            val values = e.map(_._2.parseJson.convertTo[CompleteDeployJob]).toList
            sender ! DeployJobStatus(allReadyJobMap.get(job.id).deployJob, values)

          case Failure(t) =>
            sender ! DeployJobStatus(allReadyJobMap.get(job.id).deployJob, List.empty[CompleteDeployJob])
        }
      case false =>
        cacheSelection ? RHGet(allCompleteJobKey, job.id) map(_.asInstanceOf[String]) filter("" !=) map {
          _.parseJson.convertTo[DeployJobStatus]
        } onComplete {
          case Success(e) => sender ! e
          case Failure(t) =>
            log.warning(s"fetch complete job ${job.id} failed. ", t)
            sender ! null.asInstanceOf[DeployJobStatus]
        }
    }
  }

  /**
    * 获取一台主机所有待部署的任务
    * @param getReadyDeployJob 参数是hostId
    * @return List[DeloyJob] 主机没有对应的部署任务时返回空列表
    */
  private def fetchHostAllReadyJob(getJob: GetHostReadyDeployJob): Unit = {
    cacheSelection ? RHGetAll(s"${jobReadyKey}_${getJob.host}") map {
      r => r.asInstanceOf[JHashMap[String, String]]
    } onComplete {
      case Success(s) => sender ! s.map {
        case (id, jobStr) => jobStr.toJson.convertTo[DeployJob]
      }.toList
      case Failure(t) =>
        log.warning(s"get ${getJob.host} all deploy job failed. ",t)
        sender ! List.empty[DeployJob]
    }
  }

  private def completeJob(completeJob: CompleteDeployJob): Unit = {
    val redisKey = s"${jobCompletedKey}_${completeJob.id}"
    val submitJob = allReadyJobMap.get(completeJob.id)
    cacheSelection ! RHDel(s"${jobReadyKey}_${completeJob.host}", completeJob.id)
    val future = cacheSelection ? RHLen(redisKey) map(_.asInstanceOf[Long])
    future.filter(submitJob.hosts.size-1 ==).onComplete {
      case Success(_) => margeCompleteJob(submitJob.deployJob, completeJob)
      case Failure(t) =>
        cacheSelection ! RHSet(redisKey, completeJob.host, completeJob.toJson.compactPrint)
    }
  }

  private def margeCompleteJob(job: DeployJob, completeJob: CompleteDeployJob): Unit = {
    cacheSelection ? RHGetAll(s"${jobCompletedKey}_${completeJob.id}") map {
      _.asInstanceOf[JHashMap[String,String]]
    } map(_.map(s => s._1 -> s._2.parseJson.convertTo[CompleteDeployJob])) onComplete {
      case Success(jobs) =>
        val jobStatus = DeployJobStatus(job, jobs.values.toList :+ completeJob)
        cacheSelection ! RHSet(allCompleteJobKey, completeJob.id, jobStatus.toJson.compactPrint)
        cacheSelection ! RDel(s"${jobCompletedKey}_${completeJob.id}")
        cacheSelection ! RHDel(allReadyJobKey, job.id)
        allReadyJobMap.remove(completeJob.id)

      case Failure(t) => log.warning(s"marge complete job ${job.id} failed. ",t)
    }
  }

}


object DynamicDeploy {
  val NAME = "deploy-fire-service"

  def apply(config: Config): DynamicDeploy = new DynamicDeploy(config)

  def props(config: Config): Props = Props(apply(config))
}


object DynamicDeployDataStruct {
  case class DeployJob(id: String,
                       jobType: String,
                       packageName: String,
                       script: String)
  case class SubmitDeployJob(deployJob: DeployJob, hosts: List[String])
  case class DeployJobStatus(deployJob: DeployJob, completes: List[CompleteDeployJob])
  case class GetDeployJobStatus(id: String)
  case class GetHostReadyDeployJob(host: String)
  case class CompleteDeployJob(id: String,
                               host: String,
                               fetchTime: Long,
                               completeTime: Long,
                               result: String)
}
