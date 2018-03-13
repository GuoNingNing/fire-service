package org.fire.service.restful.route.naja

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import org.fire.service.core.BaseRoute
import org.fire.service.core.ResultJsonSupport._
import spray.http.{MediaTypes, StatusCodes}
import spray.routing.Route
import akka.pattern.ask
import org.fire.service.restful.FireService

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * Created by cloud on 18/2/28.
  */
class CollectRoute(override val system : ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "naja"
  val configPrefix = "route.naja.collect"
  val fileBasePath = config.getString(s"$configPrefix.file.base.path")
  val hostInfo : ConcurrentHashMap[String,Host] = new ConcurrentHashMap[String,Host]()
  val collectDB = system.actorSelection(s"/user/${FireService.dbActorName}")
  val collectCache = system.actorSelection(s"/user/${FireService.cacheActorName}")

  import DataFormat._
  import spray.json._

  override protected def preStart(): Unit = {
    //system.scheduler.schedule(0 seconds,5 seconds,collectCache,"")
  }

  override protected def routes() : Route = {
    sourceGet()
      .~(testRest())
      .~(testEchoEntity())
      .~(pushHostInfo())
  }

  private def sourceGet(): Route = {
    (get & path("source" / RestPath)) { path =>
      getFromFile(fileBasePath + "/" + path.toString())
    }
  }

  private def testRest(): Route ={
    (post & path("rest")) {
      entity(as[String]){body =>
        respondWithMediaType(MediaTypes.`application/json`){ctx =>
          ctx.complete(StatusCodes.OK,success(s"your post body : $body"))
        }
      }
    }
  }

  private def testEchoEntity(): Route ={
    (post & path("echo")) {
      entity(as[Echo]) {echo =>
        collectDB ! TestRow(echo.time,echo.info)
        respondWithMediaType(MediaTypes.`application/json`){ctx =>
          ctx.complete(StatusCodes.OK,success(echo.info))
        }
      }
    } ~ (get & path("echo")) {
      parameters('info,'time.as[Int]).as(Echo) {echo =>
        val resFuture = collectDB ? TestRead(None)
        val res = Await.result(resFuture,timeout.duration).asInstanceOf[List[TestRow]]
        val resString = res.toJson.compactPrint
        respondWithMediaType(MediaTypes.`application/json`){ctx =>
          ctx.complete(StatusCodes.OK,success(resString))
        }
      }
    }
  }

  private def pushHostInfo(): Route = {
    path("host"){
      post {
        entity(as[Host]) { body =>
          val host = body
          hostInfo += host.hostId -> host
          collectCache ! host
          respondWithMediaType(MediaTypes.`application/json`) { ctx =>
            ctx.complete(StatusCodes.OK, success(host.hostId))
          }
        }
      } ~ get{
        parameters('id,'hostName,'timestamp.as[Long]).as(HostRow) {hostRow =>
          val host = if(hostInfo.containsKey(hostRow.id)) Some(hostInfo(hostRow.id)) else None
          respondWithMediaType(MediaTypes.`application/json`) { ctx =>
            val (status,resMsg) = host match {
              case Some(h) => (StatusCodes.OK,success(h.toJson.compactPrint))
              case None => (StatusCodes.NoContent,failure(s"can't host ${hostRow.id}"))
            }
            ctx.complete(status,resMsg)
          }
        }
      }
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

    /*val hostID = UUID.randomUUID().toString
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
    println(s"\n$hostStr\n")*/
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

