package org.fire.service.core.util

import java.io.{InputStream, OutputStream}

import scala.util.Try
import scalaj.http.{Http, HttpOptions, HttpRequest}

/**
  * Created by cloud on 18/9/3.
  */
object Utils {

  def httpGet(url: String): HttpRequest = {
    Http(url).options(HttpOptions.connTimeout(10000), HttpOptions.readTimeout(10000))
  }

  def getFile(url: String, fileName: String): Boolean = {
    val res = httpGet(url).execute { is =>
      save(fileName, is)
    }
    res.body
  }

  def save(fileName: String, content: Array[Byte]): Boolean =
    save[Array[Byte]](fileName, content, {(is, os) => os.write(is)})

  def save(fileName: String, content: InputStream): Boolean = {
    save[InputStream](fileName, content,
      { (is, os) =>
        val buffer = new Array[Byte](4096)
        Iterator
          .continually (is.read(buffer))
          .takeWhile (-1 != _)
          .foreach (read=>os.write(buffer,0,read))
      }
    )
  }

  def save[T](fileName: String, content: T, writeFile: (T, OutputStream) => Unit): Boolean = {
    Try {
      val fos = new java.io.FileOutputStream(fileName)
      writeFile(content, fos)
      fos.close()
      true
    }.getOrElse(false)
  }

}
