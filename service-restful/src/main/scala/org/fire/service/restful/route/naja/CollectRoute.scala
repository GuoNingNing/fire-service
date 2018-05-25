package org.fire.service.restful.route.naja

import java.util.UUID
import java.io.{ByteArrayInputStream, InputStream, OutputStream}

import akka.actor.{ActorRef, ActorSelection, ActorSystem, Props}
import org.fire.service.core.{BaseRoute, ResultMsg}
import org.fire.service.core.ResultJsonSupport._
import org.fire.service.restful.actor.naja._
import org.fire.service.restful.route.naja.CollectRouteConstantConfig._
import org.fire.service.restful.util.naja.{DataManager, YarnAppManager}
import spray.http._
import spray.routing.Route

import scala.slick.driver.MySQLDriver.simple._
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


/**
  * Created by cloud on 18/2/28.
  */
class CollectRoute(override val system : ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "naja"

  private val fileBasePath = config.getString(FILE_SER_PATH,FILE_SER_PATH_DEF)
  private val hostTimeout = config.getInt(HOST_TIMEOUT,HOST_TIMEOUT_DEF)
  private val mysqlConfig = config.getConfig("db")
  private val redisConfig = config.getConfig("redis")
  private var collectDBRef: ActorRef = _
  private var collectCacheRef: ActorRef = _
  private var collectLoadRef: ActorRef = _
  private var monitorRef: ActorRef = _
  private var collectDBSelect: ActorSelection = _
  private var collectCacheSelect: ActorSelection = _

  import DataFormat._
  import spray.json._

  override protected def preStart(): Unit = {
    import system.dispatcher

    actorInit()

    val restMode = config.getBoolean(DATA_MANAGER_REST,DATA_MANAGER_REST_DEF)
    restMode match {
      case true => system.scheduler.schedule (0 seconds, 5 seconds, collectLoadRef, InitWriteHosts)
      case false => system.scheduler.schedule (0 seconds, 5 seconds, collectLoadRef, InitLoadHosts)
    }

    system.scheduler.schedule(5 seconds,5 seconds,monitorRef,InitMonitor)
  }

  override protected def routes() : Route = {
    fileManager()
      .~(hostManager())
      .~(monitor())
      .~(sendMessage())
      .~(test())
  }

  private def actorInit(): Unit = {
    val mysqlConnect = Database.forConfig("mysql",mysqlConfig)
    val jedisConnect = JedisConnect(redisConfig.getConfig("connect"))

    collectDBRef = system.actorOf(CollectDBActor.props(mysqlConnect, config),CollectDBActor.NAME)
    collectCacheRef = system.actorOf(CollectCacheActor.props(jedisConnect,config),CollectCacheActor.NAME)
    collectLoadRef = system.actorOf(LoadActor.props(config),LoadActor.NAME)
    monitorRef = system.actorOf(MonitorActor.props(config),MonitorActor.NAME)

    collectDBSelect = system.actorSelection(s"/user/${CollectDBActor.NAME}")
    collectCacheSelect = system.actorSelection(s"/user/${CollectCacheActor.NAME}")
  }

  private def fileManager(): Route = {
    (get & path("source" / RestPath)) { path =>
      getFromFile(fileBasePath + "/" + path.toString())
    } ~ (post & path("upload")) {
      entity(as[MultipartFormData]) { formData =>
        Try {
          formData.fields.map {
            case BodyPart(entity, headers) =>
              val content = new ByteArrayInputStream(entity.data.toByteArray)
              val fileName = headers.find(_.is("content-disposition"))
                .getOrElse(HttpHeaders.`Content-Disposition`("", Map("filename" -> "tmp_file")))
                .value.split("filename=").last
              val saveBoolean = saveAttachment(s"$fileBasePath/$fileName", content)
              (saveBoolean, fileName)
            case _ => (false, "")
          }.head
        } match {
          case Success(res) => successJson(s"$res")
          case Failure(e) => failureJson(e.getMessage)
        }
      }
    }
  }

  private def test(): Route = {
    path("test"){
      post {
        entity(as[TestRow]) { body => successJson(body.toJson.compactPrint) }
      } ~ get {
        parameters('id.as[Int],'str).as(TestRow) { tr => successJson(tr.str) }
      }
    }
  }

  private def hostManager(): Route = {
    path("host"){
      post {
        entity(as[Host]) { body =>
          val host = body
          DataManager.hostIdMap += host.hostId -> host
          DataManager.hostNameMap += host.hostName -> host
          collectCacheRef ! host
          successJson(host.hostId)
        }
      } ~ get{
        parameters('id,'hostName,'timestamp.as[Long]).as(HostRow) {hostRow =>
          val host = getHost(hostRow)
          successJson(host.toJson.compactPrint)
        }
      }
    } ~ (get & path("hosts")) {
      def hostNameList = DataManager.hostNameMap.keys().toList.toJson.compactPrint
      respondWithMediaType(MediaTypes.`application/json`) { ctx =>
        ctx.complete(StatusCodes.OK, success(hostNameList))
      }
    }
  }

  private def monitor(): Route = pathPrefix("monitor") {
    path("host") {
      get {
        parameters('id, 'hostName, 'timestamp.as[Long]).as(HostRow) { hr =>
          val hostMonitor = DataManager.loadHistory(system, Some(hr))
          successJson(hostMonitor.toJson.compactPrint)
        }
      }
    } ~ path("memory") {
      get {
        parameters('id, 'hostName, 'timestamp.as[Long]).as(HostRow) { hr =>
          val memoryMonitor = DataManager.getMem(collectDBSelect, Some(hr))
          successJson(memoryMonitor.toJson.compactPrint)
        }
      }
    }
  }

  private def sendMessage(): Route = pathPrefix("send") {
    (post & path("ding")) {
      entity(as[Ding]) { ding =>
        monitorRef ! ding
        successJson("send ding successful")
      }
    } ~ (post & path("mail")) {
      entity(as[Mail]) { mail =>
        monitorRef ! mail
        successJson("send email successful")
      }
    } ~ (post & path("wechat")) {
      entity(as[WeChat]) { weChat =>
        monitorRef ! weChat
        successJson("send weChat successful")
      }
    }
  }

  private def appManager(): Route = pathPrefix("yarn") {
    (post & path("app")) {
      entity(as[HostJob]) { hostJob =>
        YarnAppManager.hostAppMap += hostJob.hostId -> hostJob.hostApp
        YarnAppManager.hostContainerMap += hostJob.hostId -> hostJob.hostContainer
        hostJob.hostApp.foreach(sparkApp => YarnAppManager.appIdMap += sparkApp.appId -> sparkApp)
        hostJob.hostContainer.foreach {
          container =>
            val containers = YarnAppManager.appContainerMap.getOrDefault(container.appId, List.empty[Container])
            val updateContainers = container :: containers.filter(_.containerId != container.containerId)
            YarnAppManager.appContainerMap += container.appId -> updateContainers
        }
        successJson("successful")
      }
    }
  }

  private def successJson(data: Any): Route = jsonResponse(StatusCodes.OK,success(data))
  private def failureJson(data: String): Route = jsonResponse(StatusCodes.OK,failure(data))
  private def errorJson(statusCode: StatusCode,data: String): Route = jsonResponse(statusCode,failure(data))

  private def getHost(hostRow: HostRow): List[Host] = {

    if (DataManager.hostIdMap.containsKey(hostRow.id)) {
      List(DataManager.hostIdMap(hostRow.id))
    } else if (DataManager.hostNameMap.containsKey(hostRow.hostName)) {
      List(DataManager.hostNameMap(hostRow.hostName))
    } else {
      List.empty[Host]
    }
  }

  private def saveAttachment(fileName: String, content: Array[Byte]): Boolean = {
    saveAttachment[Array[Byte]](fileName, content, {(is, os) => os.write(is)})
    true
  }

  private def saveAttachment(fileName: String, content: InputStream): Boolean = {
    saveAttachment[InputStream](fileName, content,
      { (is, os) =>
        val buffer = new Array[Byte](16384)
        Iterator
          .continually (is.read(buffer))
          .takeWhile (-1 != _)
          .foreach (read=>os.write(buffer,0,read))
      }
    )
  }

  private def saveAttachment[T](fileName: String, content: T, writeFile: (T, OutputStream) => Unit): Boolean = {
    try {
      val fos = new java.io.FileOutputStream(fileName)
      writeFile(content, fos)
      fos.close()
      true
    } catch {
      case e: Exception =>
        logger.error(s"$fileName write failed.",e)
        false
    }
  }

}





object NajaTest {
  import DataFormat._
  import spray.json._

  def nH(info : String): Host = {
    val jsonInfo = info.parseJson
    jsonInfo.convertTo[Host]
  }

  def main(args: Array[String]): Unit = {
    val echoList = Array(Echo("echo1",2220302),Echo("ohce2",2938210))
    val json = Echos("abc",echoList.toList).toJson
    val jsonStr=json.prettyPrint
    println(jsonStr)
    val color = jsonStr.parseJson.convertTo[Echos]
    println(color)

    val hostID = UUID.randomUUID().toString
    val ips = Array(IpRow(hostID,"en0","127.0.0.1",1234567890112L))
    val timestamp = System.currentTimeMillis()
    val memoryRow = MemoryRow(hostID,0L,0L,0L,0L,0L,0L,timestamp)
    val cpuRow = CpuRow(hostID,0.0,0.0,0.0,timestamp)
    val netIoRowList = Array(NetIoRow(hostID,"en0",0.0,0.0,0L,0L,timestamp))
    val diskRowList = Array(DiskRow(hostID,"/","/dev/disk0","ext4",0L,0L,0.0,0.0,timestamp))
    val processInfo = ProcessInfo(0)
    val roleRowList = Array(RoleRow(hostID,"flume",None,timestamp))
    val hostStruct = Host(hostID,
      "hostname",
      "user",
      ips.toList,
      memoryRow,
      cpuRow,
      netIoRowList.toList,
      diskRowList.toList,
      processInfo,
      roleRowList.toList)

    val hostStr = hostStruct.toJson.compactPrint
    println(s"\n$hostStr\n")
    val hStr =
      """
        |{"hostId": "6260bcd1-1b94-11e8-b9b1-9801a79f540b",
        |"role": [{"table": null, "role": "sshd.yutian", "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "timestamp": 1520848047861},
        | {"table": null, "role": "sshd.zhaozhen01", "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "timestamp": 1520848047861}],
        | "user": "zhaozhen01", "hostName": "rdqa-rd-dev00.bjcq.zybang.com",
        | "mem": {"buffer": 682360832, "used": 345579520, "shared": 151216128, "cached": 2787454976, "timestamp": 1520848047861, "total": 3977355264, "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "free": 161959936},
        | "ip": [{"ip": "192.168.240.109", "ifName": "eth0", "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "timestamp": 1520848047861}],
        | "net": [{"timestamp": 1520848047861, "totalLink": 62, "link": 50, "ifName": "eth0", "recv": 148281970.6, "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "sent": 59913416.4}],
        | "disk": [{"used": 3872157696, "ioRead": 1467518156.8, "device": "/dev/vda1", "timestamp": 1520848047861, "mount": "/", "total": 21003005952, "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "ioWrite": 30556337766.4, "fsType": "ext4"},
        | {"used": 219797569536, "ioRead": 5521471897.6, "device": "/dev/vdb1", "timestamp": 1520848047861, "mount": "/home", "total": 528312352768, "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "ioWrite": 59336143667.2, "fsType": "ext4"}],
        | "proc": {"total": 209},
        | "cpu": {"sys": 0.4, "timestamp": 1520848047861, "idle": 99.1, "id": "6260bcd1-1b94-11e8-b9b1-9801a79f540b", "user": 0.4}}
      """.stripMargin
    val host = hStr.parseJson.convertTo[Host]
    println(
      s"""
         |hostId: ${host.hostId},
         |hostName: ${host.hostName}
         |memory: ${host.mem.total}
         |cpu: ${host.cpu.idle}
         |role: ${host.role.last.role}
         |ip: ${host.ip.last.ip}
         |net: ${host.net.last.totalLink}
         |disk: ${host.disk.last.device}
         |proc: ${host.proc.total}
       """.stripMargin)

  }
}

