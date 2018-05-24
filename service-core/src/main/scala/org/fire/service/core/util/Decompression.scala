package org.fire.service.core.util

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

/**
  * Created by cloud on 18/5/24.
  */
object Decompression {

  def unTarGZIP(compressFile: String, outputDir: String): Boolean =
    unTarGZIP(new File(compressFile), new File(outputDir))

  def unTarGZIP(compressFile: File, outputDir: File): Boolean = {
    if (! createDirectory(outputDir) || ! compressFile.exists()) {
      return false
    }

    val gZIPInputStream = new GZIPInputStream(new BufferedInputStream(new FileInputStream(compressFile)))
    val archiveStreamFactory = new ArchiveStreamFactory()
    val archiveInputStream = archiveStreamFactory.createArchiveInputStream("tar",gZIPInputStream)

    Iterator.continually(archiveInputStream.getNextEntry.asInstanceOf[TarArchiveEntry])
      .takeWhile(null !=)
      .foreach { entry =>
        entry.isDirectory match {
          case true => createDirectory(new File(s"${outputDir.getAbsolutePath}/${entry.getName}"))
          case false =>
            val file = new File(s"${outputDir.getAbsolutePath}/${entry.getName}")
            createDirectory(file.getParentFile)
            val outStream = new FileOutputStream(file)
            val bufferBytes = new Array[Byte](16384)
            Iterator.continually (archiveInputStream.read(bufferBytes))
              .takeWhile(-1 !=)
              .foreach(read => outStream.write(bufferBytes,0,read))
            outStream.flush()
            outStream.close()
        }
      }
    true
  }

  def createDirectory(path: File): Boolean = {
    if (path == null) {
      return false
    }
    if (path.exists()) {
      return true
    }
    if (!path.getParentFile.exists()){
      createDirectory(path.getParentFile)
      path.mkdir()
    } else {
      path.mkdir()
    }
  }

}
