package org.fire.service.restful.actor.naja

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.actor.{Actor, ActorLogging, ActorSelection, ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.Config
import org.fire.service.restful.route.naja._
import DataFormat._
import spray.json._

import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by cloud on 18/3/13.
  */
class HostManagerActor(val config: Config) extends Actor with ActorLogging{

  import HostManagerDataStruct._
  import CacheDataStruct._
  implicit private val timeout = Timeout(20,TimeUnit.SECONDS)

  private val cacheSelection = context.actorSelection(s"/user/${CacheActor.NAME}")
  private val dbSelection = context.actorSelection(s"/user/${CollectDBActor.NAME}")

  private val historyRetainTime = 24*60*60*1000L //config.getLong("")
  private val syncCacheInterval = 3*60*60 //config.getLong("")

  override def preStart(): Unit = {
    val waitScheduled = Timeout(syncCacheInterval, TimeUnit.SECONDS)

    context.system.scheduler.schedule(waitScheduled.duration, waitScheduled.duration, self, CleanCache)
    context.system.scheduler.schedule(waitScheduled.duration, waitScheduled.duration, self, SyncCacheToDB)
  }

  override def receive: Receive = {
    case hostRead: HostRead =>
      cacheSelection ? hostRead map(s => s.asInstanceOf[List[Host]]) onComplete {
        case Success(e) => sender ! e
        case Failure(t) => sender ! List.empty[Host]
      }

    case SyncCacheToDB => syncCacheToDB()

    case CleanCache => cleanHistoryCache()

    case GetHostID(hostName) => fetchHostId(hostName) onComplete {
      case Success(s) => sender ! s
      case Failure(t) =>
        sender ! List.empty[String]
        log.warning(s"GetHostID($hostName) failed. ",t)
    }

    case host: Host => cacheSelection ! host

    case GetHostHistory(id, timestamp) => //No-op

  }

  private def fetchHostId(name: Option[String] = None): Future[List[String]] = {
    cacheSelection ? GetHostID(name) map(s => s.asInstanceOf[List[String]])
  }

  private def syncCacheToDB(): Unit = {
    fetchHostId() onComplete {
      case Success(r) =>
        r.foreach(syncHostToDB)
        log.info("sync cache to db already ready.")
      case Failure(t) =>
        log.warning("sync cache to db failed. ",t)
    }
  }

  private def syncHostToDB(id: String): Unit = {
    cacheSelection ? RRPopLPush(id, s"history_$id") map {
      s => s.asInstanceOf[String]
    } onComplete {
      case Success(s) => s match {
        case null => log.info("sync cache to db complete.")
        case _ =>
          writeHostToDB(s.parseJson.convertTo[Host])
          syncHostToDB(id)
      }
      case Failure(t) => log.warning("sync failed. ",t)
    }
  }

  private def writeHostToDB(host: Host): Unit = {
    dbSelection ! HostRow(host.hostId, host.hostName, host.mem.timestamp)
    dbSelection ! host.mem
    dbSelection ! host.cpu
    host.ip.foreach(dbSelection !)
    host.role.foreach(dbSelection !)
    host.disk.foreach(dbSelection !)
    host.net.foreach(dbSelection !)
  }

  private def cleanHistoryCache(): Unit = {
    fetchHostId() onComplete {
      case Success(s) =>
        s.foreach(cleanHistoryHost)
        log.info("clean cache already ready.")
      case Failure(t) =>
        log.warning("clean cache fetch hostId failed. ",t)
    }
  }

  private def cleanHistoryHost(id: String): Unit = {
    cacheSelection ? RLindex(s"history_$id",-1) map {
      s => s.asInstanceOf[String]
    } filter(null !=) map(_.parseJson.convertTo[Host]) filter {
      host => host.mem.timestamp < System.currentTimeMillis() - historyRetainTime
    } onComplete {
      case Success(s) =>
        cacheSelection ? RRPop(s"history_$id") map(s => s.asInstanceOf[String]) filter(null !=) onComplete {
          case Success(str) => cleanHistoryHost(id)
          case Failure(t) => log.info("clean history cache complete. ",t)
        }
      case Failure(t) => log.info("clean history cache complete. ",t)
    }
  }


}

object HostManagerActor {
  val NAME = "host-manager-fire-service"

  def apply(config: Config): HostManagerActor = new HostManagerActor(config)

  def props(config: Config): Props = Props(apply(config))
}

object HostManagerDataStruct {
  case object SyncCacheToDB
  case object CleanCache
  case class GetHostHistory(id: String, timestamp: Long)
}
