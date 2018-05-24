package org.fire.service.restful.route

import java.io.{ByteArrayInputStream, InputStream, OutputStream}

import akka.actor.ActorSystem
import org.fire.service.core.{BaseRoute, User}
import org.fire.service.core.ResultJsonSupport._
import spray.http._
import spray.routing.Route


/**
  * @param system
  */
class HelloRoute(override val system: ActorSystem) extends BaseRoute {


  override val pathPrefix: String = "hello"

  override protected def routes(): Route = {

    (get & path("world")) {
      complete {
        <h1>This is an GET method</h1>
      }
    } ~ (get & path("test")) {
      // 这样才能避免静态路由
      def now = System.currentTimeMillis()
      respondWithMediaType(MediaTypes.`application/json`) { ctx =>
        ctx.complete(StatusCodes.OK, success(now))
      }
    } ~ (post & path("world")) {
        entity(as[String]) { body =>
          respondWithMediaType(MediaTypes.`application/json`) { ctx =>
            ctx.complete(StatusCodes.OK, success(s"you post $body"))
          }
        }
      } ~ (post & path("upload" / RestPath)) { path =>
      //文件上传示例 curl -F f1=@userinfo.txt http://localhost:9200/hello/upload
      logger.info("upload req 1")
      entity(as[MultipartFormData]) { formData =>
        respondWithMediaType(MediaTypes.`application/json`) { ctx =>
          val res = formData.fields.map {
            case BodyPart(entity, headers) =>
              val content = new ByteArrayInputStream(entity.data.toByteArray)
              val fileName = headers.find(s => s.is("content-disposition"))
                .getOrElse(HttpHeaders.`Content-Disposition`("",Map("filename"->"tmp_test.txt")))
                .value.split("filename=").last
              val writeRes = saveAttachment(s"/Users/cloud/myfile/file/$fileName",content)
              (writeRes,fileName)
            case _ => (false,"")
          }
          logger.info(s"upload file $res")
          ctx.complete(StatusCodes.OK, success(s"you upload file info $res"))
        }
      }
    } ~ (get & path("download")) {

      /**
        * 文件下载示例 curl  http://localhost:9200/hello/download
        *
        * 该getFrom...指令自动将HTTP响应的内容类型设置为与文件扩展名匹配的媒体类型。
        * 例如，“.html”文件将与Content-Type: text/html
        * “.txt”文件一起收到 Content-Type: text/plain。
        * 您也可以非常方便地提供自己的媒体类型和/或文件扩展名。请参阅“ 自定义媒体类型” https://github.com/spray/spray/wiki/Custom-Media-Types。
        */

      getFromFile("userinfo.html")
    } ~ (post & path("user")) {

      entity(as[User]) { user =>
        respondWithMediaType(MediaTypes.`application/json`) { ctx =>
          logger.info(s"post user $user")
          ctx.complete(StatusCodes.OK, success(s"post user $user"))
        }
      }
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
      case _: Exception => false
    }
  }
}

