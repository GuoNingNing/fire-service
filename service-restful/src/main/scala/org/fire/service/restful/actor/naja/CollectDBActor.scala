package org.fire.service.restful.actor.naja

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import org.fire.service.restful.route.naja.CollectRouteConstantConfig._
import org.fire.service.restful.route.naja._

import scala.slick.driver.MySQLDriver.simple._

/**
  * Created by cloud on 18/3/9.
  */
class CollectDBActor(val config: Config) extends Actor with ActorLogging {

  private val db = Database.forConfig("mysql", config.getConfig("db"))

  private val test = TableQuery[Test]
  private val hosts = TableQuery[Hosts]
  private val password = TableQuery[Password]
  private val ips = TableQuery[Ips]
  private val roles = TableQuery[Roles]
  private val memory = TableQuery[Memory]
  private val cpu = TableQuery[Cpu]
  private val disk = TableQuery[Disk]
  private val netIo = TableQuery[NetIo]

  private val hostTimeout = config.getInt(HOST_TIMEOUT,HOST_TIMEOUT_DEF)

  override def receive: Receive = {
    case TestRow(id,str) => db.withSession {
      implicit session =>
        test.insertOrUpdate((id,str))
    }
    case TestRead(tr) => db.withSession {
      implicit session =>
        val response = tr match {
          case Some(t) => test.filter(_.str === t.str).list
          case None => test.list
        }
        sender ! response.map { case (id,str) => TestRow(id,str)}
    }

    case HostRow(id,hostName,timestamp) => db.withSession {
      implicit session =>
        hosts.insertOrUpdate((id,hostName,timestamp))
    }
    case HostRead(hostRow) => db.withSession {
      implicit session =>
        val hostList = hostRow match {
          case Some(x) => hosts.filter(_.id === x.id).list
          case None => hosts.filter(_.timestamp > nowTime).list
        }
        sender ! hostList.map { case (id,name,tt) => HostRow(id,name,tt)}
    }

    case PasswordRow(id,user,pass,pkey,timestamp) => db.withSession {
      implicit session =>
        password.insertOrUpdate((id,user,pass,pkey,timestamp))
    }
    case PasswordRead(p) => db.withSession {
      implicit session =>
        val passwordList = p match {
          case Some(pas) => password.filter(_.id === pas.id).list
          case None => password.list
        }
        sender ! passwordList.map { case (i, u, pw, k, t) => PasswordRow(i,u,pw,k,t) }
    }

    case IpRow(id,name,ip,timestamp) => db.withSession {
      implicit session =>
        ips.insertOrUpdate((id,name,ip,timestamp))
    }
    case IpRead(ipRow) => db.withSession {
      implicit session =>
        val ipList = ipRow match {
          case Some(i) => ips.filter(_.id === i.id).list
          case None => ips.list
        }

        sender ! ipList.map { case (i,n,p,t) => IpRow(i,n,p,t)}
    }

    case RoleRow(id,role,table,timestamp) => db.withSession {
      implicit session =>
        roles.insertOrUpdate((id,role,table,timestamp))
    }
    case RoleRead(roleRow) => db.withSession {
      implicit session =>
        val roleList = roleRow match {
          case Some(r) => roles.filter(_.id === r.id).list
          case None => roles.filter(_.timestamp > nowTime).list
        }

        sender ! roleList.map { case (i,n,t,tt) => RoleRow(i,n,t,tt)}
    }

    case MemoryRow(id,total,used,free,shared,buf,cached,timestamp) => db.withSession {
      implicit session =>
        memory += (id,total,used,free,shared,buf,cached,timestamp)
    }
    case MemoryRead(memoryRow) => db.withSession {
      implicit session =>
        val memoryRowList = memoryRow match {
          case Some(m) => memory.filter(m2 => m2.id === m.id && m2.timestamp > m.timestamp-hostTimeout).list
          case None => memory.filter(_.timestamp > nowTime).list
        }
        sender ! memoryRowList.map { case (i,t,u,f,s,b,c,tt) => MemoryRow(i,t,u,f,s,b,c,tt) }
    }

    case CpuRow(id,user,sysUse,idle,timestamp) => db.withSession {
      implicit session =>
        cpu += (id,user,sysUse,idle,timestamp)
    }
    case CpuRead(cpuRow) => db.withSession {
      implicit session =>
        val cpuRowList = cpuRow match {
          case Some(c) => cpu.filter(c2 => c2.id === c.id && c2.timestamp > c.timestamp-hostTimeout).list
          case None => cpu.filter(_.timestamp > nowTime).list
        }
        sender ! cpuRowList.map { case (id,u,s,i,tt) => CpuRow(id,u,s,i,tt) }
    }

    case DiskRow(id,mount,device,fsType,total,used,ioRead,ioWrite,timestamp) => db.withSession {
      implicit session =>
        disk += (id,mount,device,fsType,total,used,ioRead,ioWrite,timestamp)
    }
    case DiskRead(diskRow) => db.withSession {
      implicit session =>
        val diskRowList = diskRow match {
          case Some(d) => disk.filter(d2 => d2.id === d.id && d2.timestamp > d.timestamp-hostTimeout).list
          case None => disk.filter(_.timestamp > nowTime).list
        }
        sender ! diskRowList.map { case (id,m,d,f,t,u,r,w,tt) => DiskRow(id,m,d,f,t,u,r,w,tt) }
    }

    case NetIoRow(id,ifName,sent,recv,link,totalLink,timestamp) => db.withSession {
      implicit session =>
        netIo += (id,ifName,sent,recv,link,totalLink,timestamp)
    }
    case NetIoRead(netIoRow) => db.withSession {
      implicit session =>
        val netIoRowList = netIoRow match {
          case Some(n) => netIo.filter(n2 => n2.id === n.id && n2.timestamp > n.timestamp-hostTimeout).list
          case None => netIo.filter(_.timestamp > nowTime).list
        }
        sender ! netIoRowList.map { case (id,i,s,r,l,t,tt) => NetIoRow(id,i,s,r,l,t,tt) }
    }

  }

  private def nowTime = System.currentTimeMillis() - hostTimeout
}

object CollectDBActor {
  val NAME = "db-fire-service"

  def apply(config: Config): CollectDBActor = new CollectDBActor(config)

  def props(config: Config): Props = Props(apply(config))
}
