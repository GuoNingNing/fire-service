package org.fire.service.restful.route.naja

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config

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
