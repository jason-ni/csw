package csw.logging.client.utils

import java.io.File

import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable

object FileUtils {

  def deleteRecursively(file: File): Unit = {
    // just to be safe, don't delete anything other than tmt folder
    val p = file.getPath
    if (!p.contains("/tmt/"))
      throw new RuntimeException(s"Refusing to delete $file other than \'tmt\'")

    if (file.isDirectory)
      file.listFiles.foreach(deleteRecursively)
    if (file.exists && !file.delete)
      throw new Exception(s"Unable to delete ${file.getAbsolutePath}")
  }

  def read(filePath: String): mutable.Buffer[JsObject] = {
    val fileSource = scala.io.Source.fromFile(filePath)
    val logBuffer  = mutable.Buffer.empty[JsObject]

    fileSource.mkString.linesIterator.foreach { line ⇒
      logBuffer += Json.parse(line).as[JsObject]
    }
    fileSource.close()
    logBuffer
  }
}
