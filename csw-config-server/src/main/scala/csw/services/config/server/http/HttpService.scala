package csw.services.config.server.http

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.Reason
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import csw.messages.models.CoordinatedShutdownReasons.FailureReason
import csw.services.config.server.commons.{ConfigServerLogger, ConfigServiceConnection}
import csw.services.config.server.{ActorRuntime, Settings}
import csw.services.location.commons.ClusterAwareSettings
import csw.services.location.models._
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.{LogAdminActorFactory, Logger}

import scala.async.Async._
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Initialises ConfigServer
 * @param locationService          LocationService instance to be used for registering this server with the location service
 * @param configServiceRoute       ConfigServiceRoute instance representing the routes supported by this server
 * @param settings                 Runtime configuration of server
 * @param actorRuntime             ActorRuntime instance wrapper for actor system
 */
class HttpService(
    locationService: LocationService,
    configServiceRoute: ConfigServiceRoute,
    settings: Settings,
    actorRuntime: ActorRuntime
) {

  import actorRuntime._

  val log: Logger = ConfigServerLogger.getLogger

  lazy val registeredLazyBinding: Future[(ServerBinding, RegistrationResult)] = async {
    val binding            = await(bind())
    val registrationResult = await(register(binding))

    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      s"unregistering-${registrationResult.location}"
    )(() => registrationResult.unregister())

    log.info(s"Server online at http://${binding.localAddress.getHostName}:${binding.localAddress.getPort}/")
    (binding, registrationResult)
  } recoverWith {
    case NonFatal(ex) ⇒ shutdown(FailureReason(ex)).map(_ ⇒ throw ex)
  }

  def shutdown(reason: Reason): Future[Done] = actorRuntime.shutdown(reason)

  private def bind() = Http().bindAndHandle(
    handler = configServiceRoute.route,
    interface = ClusterAwareSettings.hostname,
    port = settings.`service-port`
  )

  private def register(binding: ServerBinding): Future[RegistrationResult] = {
    val registration = HttpRegistration(
      connection = ConfigServiceConnection.value,
      port = binding.localAddress.getPort,
      path = "",
      LogAdminActorFactory.make(actorSystem)
    )
    log.info(
      s"Registering Config Service HTTP Server with Location Service using registration: [${registration.toString}]"
    )
    locationService.register(registration)
  }
}
