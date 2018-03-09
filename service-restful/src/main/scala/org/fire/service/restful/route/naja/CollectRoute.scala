package org.fire.service.restful.route.naja

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import org.fire.service.core.BaseRoute
import org.fire.service.core.ResultJsonSupport._
import spray.http.{MediaTypes, StatusCodes}
import spray.routing.Route
import akka.pattern.ask

import scala.collection.JavaConversions._
import scala.concurrent.Await


/**
  * Created by cloud on 18/2/28.
  */
class CollectRoute(override val system : ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "naja"
  val configPrefix = "route.naja.collect"
  val fileBasePath = config.getString(s"$configPrefix.file.base.path")
  val hostInfo : ConcurrentHashMap[String,Host] = new ConcurrentHashMap[String,Host]()
  val collectDB = system.actorSelection("/user/collect-fire-service")

  import DataFormat._
  import spray.json._

  override protected def preStart(): Unit = {

  }

  override protected def routes() : Route = {
    sourceGet()
      .~(testRest())
      .~(testEcho())
      .~(testPostEntity())
      .~(pushHostInfo())
  }

  private def sourceGet(): Route = {
    (get & path("source" / RestPath)) { path =>
      getFromFile(fileBasePath + "/" + path.toString())
    }
  }

  private def testEcho(): Route ={
    (get & path("echo")) {
      parameters('info,'time.as[Int]).as(Echo) { echo =>
        complete {
          <h3>INFO: {echo.info.reverse},TIME: {echo.time}</h3>
        }
      }
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

  private def testPostEntity(): Route ={
    (post & path("post")) {
      entity(as[Echo]) {echo =>
        collectDB ! TestRow(echo.time,echo.info)
        respondWithMediaType(MediaTypes.`application/json`){ctx =>
          ctx.complete(StatusCodes.OK,success(echo.info))
        }
      }
    } ~ (get & path("post")) {
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
          respondWithMediaType(MediaTypes.`application/json`) { ctx =>
            ctx.complete(StatusCodes.OK, success(host.hostId))
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
    val host = hostStr.parseJson.convertTo[Host]
    println(host.hostId)

  }
}

