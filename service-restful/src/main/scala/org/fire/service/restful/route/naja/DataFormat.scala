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
  implicit val dingFormat = jsonFormat3(Ding)
  implicit val mailFormat = jsonFormat10(Mail)
  implicit val weChatFormat = jsonFormat3(WeChat)
  implicit val containerFormat = jsonFormat4(Container)
  implicit val sparkAppFormat = jsonFormat6(SparkApp)
  implicit val hostJobFormat = jsonFormat3(HostJob)
}

/*
* MonitorActor 需要的一些消息体,用来限定用哪种方式发送消息
* */
sealed trait SendMsg extends Serializable{
  val msg: String
}
case class Ding(url: String,
                contacts: Seq[String],
                msg: String) extends SendMsg
case class Mail(server: String,
                port: Int,
                user: String,
                password: String,
                from: (String,String),
                to: Seq[String],
                cc: Seq[String] = Seq.empty,
                bcc: Seq[String] = Seq.empty,
                subject: String,
                msg: String) extends SendMsg
case class WeChat(url: String,
                  contacts: Seq[String],
                  msg: String) extends SendMsg


/*
*
* 数据库表接口
* */
sealed trait Row extends Serializable{
  val id: String
  val timestamp: Long
}
sealed trait RowRead extends Serializable

case class ProcessInfo(total: Int)


/*
* Container 表示yarn job的appId和containerId以及所在主机Id
* HostContainer 表示一台主机上含有的container数量
* */
case class Container(hostId: String, appId: String, containerId: String, timestamp: Long)
case class SparkApp(hostId: String,
                    appId: String,
                    userClass: String,
                    appName: String,
                    containers: List[Container],
                    timestamp: Long)
case class HostJob(hostId: String, hostApp: List[SparkApp], hostContainer: List[Container])

/*
* 在内存中存在的一台主机的数据结构
* redis中存的是此结构对应的json数据
* */
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

/*
* 前端请求一台主机的监控数据返回此结构
* */
case class HostMonitor(hostId: String,
                       hostName: String,
                       role: List[RoleRow],
                       cpu: List[CpuRow],
                       mem: List[MemoryRow],
                       disk: List[DiskRow],
                       net: List[NetIoRow])

/*
* 测试用表,没用处
* */
case class Echo(info: String,time: Int)
case class Echos(a : String,b : List[Echo])
case class TestRow(id: Int, str: String)
case class TestRead(testRow: Option[TestRow]) extends RowRead
class Test(tag: Tag) extends Table[(Int,String)](tag,"test"){
  def id = column[Int]("id",O.PrimaryKey)
  def str = column[String]("str")

  def * = (id,str)
}


/*
* 主机表
* 由id[UUID]确定唯一主机,主机id由第一次上报信息时client确定,生成后不会再变
* 主机名可以变更
* 时间字段超过一定时间未更新表明主机已失联,具体时间由用户确定
* */
case class HostRow(id: String,
                   hostName: String,
                   timestamp: Long) extends Row

case class HostRead(hostRow: Option[HostRow]) extends RowRead

class Hosts(tag: Tag) extends Table[(String,String,Long)](tag,"hosts"){
  def id = column[String]("host_id",O.PrimaryKey)
  def name = column[String]("host_name")
  def timestamp = column[Long]("timestamp")

  def * = (id,name,timestamp)
}


/*
* 密码表,密码由用户手动配置,
* publicKey为私钥的存储路径,具体是本地路径还是远程url由用户决定在何处使用此私钥来确定
* */
case class PasswordRow(id: String,
                       user: String,
                       password: String,
                       publicKey: String,
                       timestamp: Long) extends Row

case class PasswordRead(password: Option[PasswordRow]) extends RowRead

class Password(tag: Tag) extends Table[(String,String,String,String,Long)](tag,"password"){
  def id = column[String]("host_id",O.PrimaryKey)
  def user = column[String]("host_user",O.PrimaryKey)
  def password = column[String]("passwd")
  def publicKey = column[String]("public_key")
  def timestamp = column[Long]("timestamp")

  def * = (id,user,password,publicKey,timestamp)
}


/*
* ip表 网卡名称和hostid确定唯一主机网卡,网卡不能改名,可以更换ip
* */
case class IpRow(id: String,
                 ifName: String,
                 ip: String,
                 timestamp: Long) extends Row

case class IpRead(ipRow: Option[IpRow]) extends RowRead

class Ips(tag: Tag) extends Table[(String,String,String,Long)](tag,"ips"){
  def id = column[String]("host_id",O.PrimaryKey)
  def ifName = column[String]("host_ifname",O.PrimaryKey)
  def ip = column[String]("host_ip")
  def timestamp = column[Long]("timestamp")

  def * = (id,ifName,ip,timestamp)
}


/*
* 角色表 时间字段超过一定时间未更新表明角色已经消失
* */
case class RoleRow(id: String,
                   role: String,
                   table: Option[String],
                   timestamp: Long) extends Row

case class RoleRead(roleRow: Option[RoleRow]) extends RowRead

class Roles(tag: Tag) extends Table[(String,String,Option[String],Long)](tag,"roles"){
  def id = column[String]("host_id",O.PrimaryKey)
  def role = column[String]("host_role",O.PrimaryKey)
  def table = column[Option[String]]("table_name")
  def timestamp = column[Long]("timestamp")

  def * = (id,role,table,timestamp)
}


/*
* 内存表
* */
case class MemoryRow(id: String,
                     total: Long,
                     used: Long,
                     free: Long,
                     shared: Long,
                     buffer: Long,
                     cached: Long,
                     timestamp: Long) extends Row

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


/*
* cpu表
* */
case class CpuRow(id: String,
                  user: Double,
                  sys: Double,
                  idle: Double,
                  timestamp: Long) extends Row

case class CpuRead(cpuRow: Option[CpuRow]) extends RowRead

class Cpu(tag: Tag) extends Table[(String,Double,Double,Double,Long)](tag,"cpu"){
  def id = column[String]("host_id")
  def user = column[Double]("userd")
  def sys = column[Double]("sys")
  def idle = column[Double]("idle")
  def timestamp = column[Long]("timestamp")

  def * = (id,user,sys,idle,timestamp)
}


/*
* 磁盘表
* */
case class DiskRow(id: String,
                   mount: String,
                   device: String,
                   fsType: String,
                   total: Long,
                   used:Long,
                   ioRead: Double,
                   ioWrite: Double,
                   timestamp: Long) extends Row

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


/*
* 网络流量表
* */
case class NetIoRow(id: String,
                    ifName: String,
                    sent: Double,
                    recv: Double,
                    link: Long,
                    totalLink: Long,
                    timestamp: Long) extends Row

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


