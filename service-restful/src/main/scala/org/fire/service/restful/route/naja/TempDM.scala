package org.fire.service.restful.route.naja

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.actor.{ActorSelection, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import scala.collection.mutable.{ListBuffer, Map => IMap}

/**
  * Created by cloud on 18/3/9.
  */
object TempDM {

  implicit val timeout = Timeout(20,TimeUnit.SECONDS)

  val hostIdMap = new ConcurrentHashMap[String,Host]()
  val hostNameMap = new ConcurrentHashMap[String,Host]()

  def hostStruct(actorSystem: ActorSystem): List[ConcurrentHashMap[String,Host]] = {
    val collectDB = actorSystem.actorSelection("/user/collect-db-fire-service")
    val hostsFuture = collectDB ? HostRead(None)
    val hosts = Await.result(hostsFuture,timeout.duration).asInstanceOf[List[HostRow]]
    val roles = getRole(collectDB,None)
    val ips = getIp(collectDB,None)
    val disks = getDisk(collectDB,None)
    val nets = getNet(collectDB,None)
    val cpus = getCpu(collectDB,None)
    val memorys = getMem(collectDB,None)
    hosts.foreach {
      h =>
        val hm = h.id -> Host(
          h.id,
          h.hostName,
          "",
          ips(h.id),
          memorys(h.id),
          cpus(h.id),
          nets(h.id),
          disks(h.id),
          ProcessInfo(0),
          roles(h.id))
        hostIdMap += hm
        hostNameMap += hm
    }
    Array(hostIdMap,hostNameMap).toList
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
