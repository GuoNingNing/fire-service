package org.fire.service.restful.route.naja

import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import scala.slick.driver.MySQLDriver.simple._

/**
  * Created by cloud on 18/3/2.
  */
object DataFormat extends DefaultJsonProtocol with SprayJsonSupport{
  implicit val hostRowFormat = jsonFormat3(HostRow)
  implicit val testRowFormat = jsonFormat2(TestRow)
  implicit val ipRowFormat = jsonFormat4(IpRow)
  implicit val echoFormat = jsonFormat2(Echo)
  implicit val memFormat = jsonFormat8(MemoryRow)
  implicit val cpuFormat = jsonFormat5(CpuRow)
  implicit val netCardFormat = jsonFormat7(NetIoRow)
  implicit val processInfoFormat = jsonFormat1(ProcessInfo)
  implicit val roleFormat = jsonFormat4(RoleRow)
  implicit val diskFormat = jsonFormat9(DiskRow)
  implicit val hostFormat = jsonFormat10(Host)
  implicit val hostMonitorFormat = jsonFormat7(HostMonitor)
  implicit val colorFormat = jsonFormat2(Echos.apply)
}


/*
* 用来推动定时进行数据同步用的消息,在LoadActor中被需要
* */
case object InitLoadHosts
case object InitWriteHosts
/*
* 用来推动定时进行数据监控的消息,在MonitorActor中被需要
* */
case object InitMonitor

/*
* MonitorActor 需要的一些消息体,用来限定用哪种方式发送消息
* */
sealed trait SendMsg extends Serializable{
  val msg: String
}
case class Ding(url: String,
                contacts: Seq[String],
                override val msg: String) extends SendMsg
case class Mail(server: String,
                port: Int,
                user: String,
                password: String,
                from: (String,String),
                to: Seq[String],
                cc: Seq[String] = Seq.empty,
                bcc: Seq[String] = Seq.empty,
                subject: String,
                override val msg: String) extends SendMsg
case class WeChat(url: String,
                  contacts: Seq[String],
                  override val msg: String) extends SendMsg
object Ding {
  def apply(url: String, contacts: String, msg: String): Ding =
    new Ding(url, contacts.split(",").toSeq, msg)
}
object Mail {
  val qqSmtpServer = "smtp.exmail.qq.com"
  val qqSmtpPort = 465

  def apply(user: String,
            password: String,
            from: (String,String),
            to: Seq[String],
            subject: String,
            msg: String): Mail =
    new Mail(qqSmtpServer,qqSmtpPort,user,password, from,to,subject = subject,msg = msg)

  def apply(from: String,
            password: String,
            to: String,
            subject: String,
            msg: String): Mail =
    apply(from,password,from -> from.substring(0,from.indexOf("@")),to.split(",").toSeq,subject,msg)
}
object WeChat {
  def apply(url: String, contacts: String, msg: String): WeChat =
    new WeChat(url, contacts.split(","), msg)
}

/*
* redis 数据结构
* */
sealed abstract class RedisCMD extends Serializable
case class RSet(key: String,value: String) extends RedisCMD
case class RGet(key: String) extends RedisCMD


/*
*
* 数据库表接口
* */
trait Row extends Serializable{
  val id: String
  val timestamp: Long
}
trait RowRead extends Serializable

case class Echo(info: String,time: Int)
case class ProcessInfo(total: Int)
case class Host(hostId : String,
                hostName : String,
                user : String,
                ip : List[IpRow],
                mem : MemoryRow,
                cpu : CpuRow,
                net : List[NetIoRow],
                disk : List[DiskRow],
                proc : ProcessInfo,
                role : List[RoleRow])
case class HostMonitor(hostId: String,
                       hostName: String,
                       role: List[RoleRow],
                       cpu: List[CpuRow],
                       mem: List[MemoryRow],
                       disk: List[DiskRow],
                       net: List[NetIoRow])

case class Echos(a : String,b : List[Echo])


case class TestRow(id: Int,str: String)
case class TestRead(testRow: Option[TestRow]) extends RowRead
class Test(tag: Tag) extends Table[(Int,String)](tag,"test"){
  def id = column[Int]("id",O.PrimaryKey)
  def str = column[String]("str")

  def * = (id,str)
}

case class HostRow(override val id: String,hostName: String,
                   override val timestamp: Long) extends Row
case class HostRead(hostRow: Option[HostRow]) extends RowRead
class Hosts(tag: Tag) extends Table[(String,String,Long)](tag,"hosts"){
  def id = column[String]("host_id",O.PrimaryKey)
  def name = column[String]("host_name")
  def timestamp = column[Long]("timestamp")

  def * = (id,name,timestamp)
}

case class PasswordRow(override val id: String,user: String,password: String,publicKey: String,
                       override val timestamp: Long) extends Row
case class PasswordRead(password: Option[PasswordRow]) extends RowRead
class Password(tag: Tag) extends Table[(String,String,String,String,Long)](tag,"password"){
  def id = column[String]("host_id",O.PrimaryKey)
  def user = column[String]("host_user",O.PrimaryKey)
  def password = column[String]("passwd")
  def publicKey = column[String]("public_key")
  def timestamp = column[Long]("timestamp")

  def * = (id,user,password,publicKey,timestamp)
}

case class IpRow(override val id: String,ifName: String,ip: String,
                 override val timestamp: Long) extends Row
case class IpRead(ipRow: Option[IpRow]) extends RowRead
class Ips(tag: Tag) extends Table[(String,String,String,Long)](tag,"ips"){
  def id = column[String]("host_id",O.PrimaryKey)
  def ifName = column[String]("host_ifname",O.PrimaryKey)
  def ip = column[String]("host_ip")
  def timestamp = column[Long]("timestamp")

  def * = (id,ifName,ip,timestamp)
}

case class RoleRow(override val id: String,role: String,table: Option[String],
                   override val timestamp: Long) extends Row
case class RoleRead(roleRow: Option[RoleRow]) extends RowRead
class Roles(tag: Tag) extends Table[(String,String,Option[String],Long)](tag,"roles"){
  def id = column[String]("host_id",O.PrimaryKey)
  def role = column[String]("host_role",O.PrimaryKey)
  def table = column[Option[String]]("table_name")
  def timestamp = column[Long]("timestamp")

  def * = (id,role,table,timestamp)
}

case class MemoryRow(override val id: String,
                     total: Long,used: Long,free: Long,
                     shared: Long,buffer: Long,cached: Long,
                     override val timestamp: Long) extends Row
case class MemoryRead(memoryRow: Option[MemoryRow]) extends RowRead
class Memory(tag: Tag) extends Table[(String,Long,Long,Long,Long,Long,Long,Long)](tag,"mem"){
  def id = column[String]("host_id")
  def total = column[Long]("total")
  def used = column[Long]("used")
  def free = column[Long]("free")
  def shared = column[Long]("shared")
  def buffer = column[Long]("buffer")
  def cached = column[Long]("cached")
  def timestamp = column[Long]("timestamp")

  def * = (id,total,used,free,shared,buffer,cached,timestamp)
}

case class CpuRow(override val id: String,user: Double,sys: Double,idle: Double,
                  override val timestamp: Long) extends Row
case class CpuRead(cpuRow: Option[CpuRow]) extends RowRead
class Cpu(tag: Tag) extends Table[(String,Double,Double,Double,Long)](tag,"cpu"){
  def id = column[String]("host_id")
  def user = column[Double]("userd")
  def sys = column[Double]("sys")
  def idle = column[Double]("idle")
  def timestamp = column[Long]("timestamp")

  def * = (id,user,sys,idle,timestamp)
}

case class DiskRow(override val id: String,mount: String,device: String,fsType: String,
                   total: Long,used:Long,
                   ioRead: Double,ioWrite: Double,
                   override val timestamp: Long) extends Row
case class DiskRead(diskRow: Option[DiskRow]) extends RowRead
class Disk(tag: Tag) extends Table[(String,String,String,String,Long,Long,Double,Double,Long)](tag,"disk"){
  def id = column[String]("host_id")
  def mount = column[String]("mount")
  def device = column[String]("device")
  def fsType = column[String]("fstype")
  def total = column[Long]("total")
  def used = column[Long]("used")
  def ioRead = column[Double]("io_read")
  def ioWrite = column[Double]("io_write")
  def timestamp = column[Long]("timestamp")

  def * = (id,mount,device,fsType,total,used,ioRead,ioWrite,timestamp)
}

case class NetIoRow(override val id: String,ifName: String,
                    sent: Double,recv: Double,
                    link: Long,totalLink: Long,
                    override val timestamp: Long) extends Row
case class NetIoRead(netIoRow: Option[NetIoRow]) extends RowRead
class NetIo(tag: Tag) extends Table[(String,String,Double,Double,Long,Long,Long)](tag,"net_io"){
  def id = column[String]("host_id")
  def ifName = column[String]("host_ifname")
  def send = column[Double]("sent")
  def receive = column[Double]("recv")
  def link = column[Long]("link_num")
  def totalLink = column[Long]("total_link")
  def timestamp = column[Long]("timestamp")

  def * = (id,ifName,send,receive,link,totalLink,timestamp)
}


