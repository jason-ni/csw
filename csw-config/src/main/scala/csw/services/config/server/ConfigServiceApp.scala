package csw.services.config.server

import java.io.File
import java.nio.file.Paths

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.server.{Directive1, HttpApp, Route}
import csw.services.config.internal.JsonSupport
import csw.services.config.models.{ConfigData, ConfigId, ConfigSource}
import csw.services.config.scaladsl.ConfigManager

class ConfigServiceApp(configManager: ConfigManager) extends HttpApp with JsonSupport {

  private val actorSystem = ActorSystem()

  import actorSystem.dispatcher

  val pathParam: Directive1[File] = parameter('path).map(filePath ⇒ Paths.get(filePath).toFile)
  val idParam: Directive1[Option[ConfigId]] = parameter('id.?).map(_.map(new ConfigId(_)))
  val maxResultsParam: Directive1[Int] = parameter('maxResults.as[Int] ? Int.MaxValue)
  val commentParam: Directive1[String] = parameter('comment ? "")
  val oversizeParam: Directive1[Boolean] = parameter('oversize.as[Boolean] ? false)
  val fileDataParam: Directive1[ConfigSource] = fileUpload("conf").map { case (_, source) ⇒ ConfigSource(source) }

  implicit val configDataMarshaller: ToEntityMarshaller[ConfigData] = Marshaller.opaque { configData =>
    Chunked.fromData(ContentTypes.`application/octet-stream`, configData.source)
  }

  override protected def route: Route = {
    get {
      path("get") {
        (pathParam & idParam) { (filePath, maybeConfigId) ⇒
          rejectEmptyResponse & complete {
            configManager.get(filePath, maybeConfigId)
          }
        }
      } ~
        path("getDefault") {
          pathParam { filePath ⇒
            rejectEmptyResponse & complete {
              configManager.getDefault(filePath)
            }
          }
        } ~
        path("exists") {
          pathParam { filePath ⇒
            rejectEmptyResponse & complete {
              configManager.exists(filePath).map { found ⇒
                if (found) Some(Done) else None
              }
            }
          }
        } ~
        path("list") {
          complete(configManager.list())
        } ~
        path("history") {
          (pathParam & maxResultsParam) { (filePath, maxCount) ⇒
            complete(configManager.history(filePath, maxCount))
          }
        }
    } ~
      post {
        path("create") {
          (pathParam & fileDataParam & oversizeParam & commentParam) { (filePath, configSource, oversize, comment) ⇒
            complete(configManager.create(filePath, configSource, oversize, comment))
          }
        } ~
          path("update") {
            (pathParam & fileDataParam & commentParam) { (filePath, configSource, comment) ⇒
              complete(configManager.update(filePath, configSource, comment))
            }
          } ~
          path("setDefault") {
            (pathParam & idParam) { (filePath, maybeConfigId) ⇒
              complete(configManager.setDefault(filePath, maybeConfigId).map(_ ⇒ Done))
            }
          } ~
          path("resetDefault") {
            pathParam { filePath ⇒
              complete(configManager.resetDefault(filePath).map(_ ⇒ Done))
            }
          }
      }
  }
}
