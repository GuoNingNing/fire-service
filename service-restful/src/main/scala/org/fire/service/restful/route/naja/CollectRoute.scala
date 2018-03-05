package org.fire.service.restful.route.naja

import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import org.fire.service.core.BaseRoute
import org.fire.service.core.ResultJsonSupport._
import spray.http.{MediaTypes, StatusCodes}
import spray.routing.Route

import scala.collection.JavaConversions._


/**
  * Created by cloud on 18/2/28.
  */
class CollectRoute(override val system : ActorSystem) extends BaseRoute{
  override val pathPrefix: String = "naja"
  val configPrefix = "route.naja.collect"
  val fileBasePath = config.getString(s"$configPrefix.file.base.path")
  val hostInfo : ConcurrentHashMap[String,Host] = new ConcurrentHashMap[String,Host]()

  import DataFormat._

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
        respondWithMediaType(MediaTypes.`application/json`){ctx =>
          ctx.complete(StatusCodes.OK,success(echo.info))
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
    val json = Color("abc",echoList.toList).toJson
    val jsonStr=json.prettyPrint
    println(jsonStr)
    val color = jsonStr.parseJson.convertTo[Color]
    println(color)

    val hostStr=
      """
        |{"disk":[{"used":200000,"read":12.2,"fsType":"ext3","total":2000000,"percent":2.200000047683716,"mount":"/","write":14.2,"device":"/dev/vda1"}],"role":[{"name":"t1","status":2,"info":""},{"name":"t2","status":3,"info":""}],"hostName":"nn","net":[{"name":"eth0","ip":"192.168.1.1","link":20,"totalLink":30},{"name":"lo","ip":"127.0.0.1","link":0,"totalLink":30}],"mem":{"used":2000,"cached":0,"shared":0,"buffer":0,"total":20000,"free":20000},"cpu":{"idle":93.30000305175781,"user":2.4000000953674316,"sys":3.9000000953674316},"hostId":"uid-00","proc":{"total":20},"user":"cloud"}
      """.stripMargin
    val host = hostStr.parseJson.convertTo[Host]
    println(host.hostId)

  }
}

