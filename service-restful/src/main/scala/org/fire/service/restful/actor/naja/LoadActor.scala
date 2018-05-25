package org.fire.service.restful.actor.naja

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import org.fire.service.restful.route.naja._
import org.fire.service.restful.util.naja.DataManager

/**
  * Created by cloud on 18/3/13.
  */
class LoadActor(config: Config) extends Actor with ActorLogging{

  override def receive: Receive = {
    case hostRow: HostRow =>
      val hostMap = DataManager.loadHosts(context.system, Some(hostRow))
      DataManager.updateCache(context.system, hostMap)

    case HostRead(hostRow) =>
      val res = hostRow match {
        case Some(h) => DataManager.parseLoadHosts(context.system,hostRow)
        case None => DataManager.parseLoadHosts(context.system,None)
      }
      sender ! res

    case InitLoadHosts =>
      val hostMap = DataManager.loadHosts(context.system, None)
      DataManager.updateCache(context.system, hostMap)

    case InitWriteHosts =>
      DataManager.writeHosts(context.system,DataManager.parseLoadHosts(context.system,None))

    case host: Host => DataManager.writeHosts(context.system,List(host))

  }
}

object LoadActor {
  val NAME = "load-fire-service"

  def apply(config: Config): LoadActor = new LoadActor(config)

  def props(config: Config): Props = Props(apply(config))
}
