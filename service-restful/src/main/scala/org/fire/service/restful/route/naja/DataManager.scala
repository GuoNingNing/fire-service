package org.fire.service.restful.route.naja

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.actor.{ActorSelection, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import org.fire.service.restful.FireService

import scala.concurrent.Await
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import scala.collection.mutable.{ListBuffer, Map => IMap}

/**
  * Created by cloud on 18/3/9.
  */
object DataManager {

  implicit val timeout = Timeout(20,TimeUnit.SECONDS)

  val hostIdMap = new ConcurrentHashMap[String,Host]()
  val hostNameMap = new ConcurrentHashMap[String,Host]()
  val cacheActorName = FireService.cacheActorName
  val dbActorName = FireService.dbActorName

  def parseLoadHosts(actorSystem: ActorSystem,hostRow: Option[HostRow]): List[Host] = {
    val collectCache = actorSystem.actorSelection(s"/user/$cacheActorName")
    val redisFuture = collectCache ? HostRead(hostRow)
    val res = Await.result(redisFuture, timeout.duration).asInstanceOf[Seq[String]]
    res.map { hostStr =>
      import DataFormat._
      import spray.json._
      hostStr.parseJson.convertTo[Host]
    }.toList
  }

  def updateCache(actorSystem: ActorSystem, hostMap: Map[String,Host]): Unit = {
    val collectCache = actorSystem.actorSelection(s"/user/$cacheActorName")

    hostMap.foreach { case (id,host) =>
      collectCache ! host
    }
  }

  def writeCache(actorSystem: ActorSystem,host: Host): Unit = {
    val collectCache = actorSystem.actorSelection(s"/user/$cacheActorName")
    collectCache ! Host
  }
  def writeHosts(actorSystem: ActorSystem, hostList: List[Host]): Unit = {
    val collectDB = actorSystem.actorSelection(s"/user/$dbActorName")
    hostList.foreach {h =>
      collectDB ! HostRow(h.hostId,h.hostName,h.cpu.timestamp)
      collectDB ! h.mem
      collectDB ! h.cpu
      h.ip.foreach(i => collectDB ! i)
      h.role.foreach(r => collectDB ! r)
      h.disk.foreach(d => collectDB ! d)
      h.net.foreach(n => collectDB ! n)
    }
  }


  def loadHosts(actorSystem: ActorSystem,hostRow: Option[HostRow] = None): Map[String,Host] = {
    val collectDB = actorSystem.actorSelection(s"/user/$dbActorName")
    val hostsFuture = collectDB ? HostRead(hostRow)
    val hosts = Await.result(hostsFuture,timeout.duration).asInstanceOf[List[HostRow]]
    val roles = getRole(collectDB,hostRow)
    val ips = getIp(collectDB,hostRow)
    val disks = getDisk(collectDB,hostRow)
    val nets = getNet(collectDB,hostRow)
    val cpus = getCpu(collectDB,hostRow)
    val memorys = getMem(collectDB,hostRow)
    hosts.map {
      case HostRow(id,name,_) =>
        id -> Host(id, name, "", ips(id), memorys(id), cpus(id), nets(id), disks(id), ProcessInfo(0), roles(id))
    }.toMap
  }

  def getNameRow[T <: RowRead,R <: Row,K : ClassTag](collectDB: ActorSelection, rowRead: T)
                                                    (f: R => K): Map[String,List[R]] = {
    val diskRowMap = IMap[String,List[R]]()
    val future = collectDB ? rowRead
    val res = Await.result(future,timeout.duration).asInstanceOf[List[R]]
    res.groupBy(_.id).foreach {
      case (id,ld) =>
        val diskRowList = ListBuffer[R]()
        ld.groupBy(f).foreach {
          case (mount,ml) => diskRowList += ml.sortBy(_.timestamp).last
        }
        diskRowMap += id -> diskRowList.toList
    }
    diskRowMap.toMap
  }

  def getPassword(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val passwordRead = hostRow match {
      case Some(hr) => PasswordRead(Some(PasswordRow(hr.id,"","","",hr.timestamp)))
      case None => PasswordRead(None)
    }
    getNameRow[PasswordRead,PasswordRow,String](collectDB,passwordRead) {p => p.user}
  }
  def getIp(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val ipRead = hostRow match {
      case Some(hr) => IpRead(Some(IpRow(hr.id,"","",hr.timestamp)))
      case None => IpRead(None)
    }
    getNameRow[IpRead,IpRow,String](collectDB,ipRead) {i => i.ifName}
  }
  def getRole(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val roleRead = hostRow match {
      case Some(hr) => RoleRead(Some(RoleRow(hr.id,"",None,hr.timestamp)))
      case None => RoleRead(None)
    }
    getNameRow[RoleRead,RoleRow,String](collectDB,roleRead) {r => r.role}
  }
  def getDisk(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val diskRead = hostRow match {
      case Some(hr) => DiskRead(Some(DiskRow(hr.id,"","","",0L,0L,0,0,hr.timestamp)))
      case None => DiskRead(None)
    }

    getNameRow[DiskRead,DiskRow,String](collectDB,diskRead) {d => d.mount}
  }
  def getNet(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val netIoRead = hostRow match {
      case Some(hr) => NetIoRead(Some(NetIoRow(hr.id,"",0,0,0L,0L,hr.timestamp)))
      case None => NetIoRead(None)
    }
    getNameRow[NetIoRead,NetIoRow,String](collectDB,netIoRead) {n => n.ifName}
  }


  def getRow[T <: RowRead,R <: Row](ref: ActorSelection,rowRead: T): Map[String,R] = {
    val future = ref ? rowRead
    val res = Await.result(future,timeout.duration).asInstanceOf[List[R]]
    res.groupBy(_.id).map { case (id,lc) => id -> lc.sortBy(_.timestamp).last }
  }

  def getCpu(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val cpuRead = hostRow match {
      case Some(hr) => CpuRead(Some(CpuRow(hr.id,0,0,0,hr.timestamp)))
      case None => CpuRead(None)
    }
    getRow[CpuRead, CpuRow](collectDB, cpuRead)
  }
  def getMem(collectDB: ActorSelection,hostRow: Option[HostRow]) = {
    val memoryRead = hostRow match {
      case Some(hr) => MemoryRead(Some(MemoryRow(hr.id,0L,0L,0L,0L,0L,0L,hr.timestamp)))
      case None => MemoryRead(None)
    }
    getRow[MemoryRead, MemoryRow](collectDB, memoryRead)
  }


}
