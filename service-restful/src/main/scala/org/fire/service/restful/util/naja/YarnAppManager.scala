package org.fire.service.restful.util.naja

import java.util.concurrent.ConcurrentHashMap

import org.fire.service.restful.actor.naja.MonitorManager
import org.fire.service.restful.route.naja.{Container, SparkApp}

import scala.collection.JavaConversions._

/**
  * Created by cloud on 18/3/30.
  */
object YarnAppManager {
  val hostAppMap = new ConcurrentHashMap[String,List[SparkApp]]()
  val hostContainerMap = new ConcurrentHashMap[String,List[Container]]()
  val appContainerMap = new ConcurrentHashMap[String,List[Container]]()
  val appIdMap = new ConcurrentHashMap[String,SparkApp]()
  val appHostMap = new ConcurrentHashMap[String,String]()

  def fetchSparkApp(appId: String, timeout: Long): Option[SparkApp] ={
    val nonTimeout = (container: Container) => container.timestamp > System.currentTimeMillis() - timeout
    appIdMap.contains(appId) match {
      case true =>
        val sparkApp = appIdMap(appId)
        val otherContainers = appContainerMap.getOrDefault(appId,List.empty[Container])
          .filter(nonTimeout)
        val container = sparkApp.containers ::: otherContainers
        val newSparkApp =
          SparkApp(sparkApp.hostId,sparkApp.appId,sparkApp.userClass,sparkApp.appName,container,sparkApp.timestamp)
        Some(newSparkApp)
      case false => None
    }
  }
}
