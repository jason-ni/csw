package csw.location.agent

import akka.Done
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.stream.ActorMaterializer
import csw.location.agent.commons.CoordinatedShutdownReasons.{FailureReason, ProcessTerminated}
import csw.location.agent.commons.LocationAgentLogger
import csw.location.agent.models.Command
import csw.location.api.models.Connection.TcpConnection
import csw.location.api.models.{ComponentId, ComponentType, RegistrationResult, TcpRegistration}
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.scaladsl.Logger

import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.sys.process._
import scala.util.control.NonFatal

/**
 * Starts a given external program ([[TcpConnection]]), registers it with the location service and unregisters it when the program exits.
 */
class LocationAgent(names: List[String], command: Command, actorSystem: ActorSystem) {
  private val log: Logger = LocationAgentLogger.getLogger

  implicit val mat: ActorMaterializer          = ActorMaterializer()(actorSystem)
  implicit val ec: ExecutionContext            = actorSystem.dispatcher
  val coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(actorSystem)

  private val locationService = HttpLocationServiceFactory.makeLocalClient(actorSystem, mat)

  // registers provided list of service names with location service
  // and starts a external program in child process using provided command
  def run(): Process =
    try {
      log.info(s"Executing specified command: ${command.commandText}")
      val process = command.commandText.run()
      // shutdown location agent on termination of external program started using provided command
      Future(process.exitValue()).onComplete(_ ⇒ shutdown(ProcessTerminated))

      // delay the registration of component after executing the command
      Thread.sleep(command.delay)

      //Register all connections
      val results = Await.result(Future.traverse(names)(registerName), 10.seconds)
      unregisterOnTermination(results)

      process
    } catch {
      case NonFatal(ex) ⇒
        shutdown(FailureReason(ex))
        throw ex
    }

  // ================= INTERNAL API =================

  // Registers a single service as a TCP service
  private def registerName(name: String): Future[RegistrationResult] = {
    val componentId = ComponentId(name, ComponentType.Service)
    val connection  = TcpConnection(componentId)
    locationService.register(TcpRegistration(connection, command.port))
  }

  // Registers a shutdownHook to handle service un-registration during abnormal exit
  private def unregisterOnTermination(results: Seq[RegistrationResult]): Unit = {

    // Add task to unregister the TcpRegistration from location service
    // This task will get invoked before shutting down actor system
    coordinatedShutdown.addTask(
      CoordinatedShutdown.PhaseBeforeServiceUnbind,
      "unregistering"
    )(() => unregisterServices(results))
  }

  private def unregisterServices(results: Seq[RegistrationResult]): Future[Done] = {
    log.info("Shutdown hook reached, un-registering connections", Map("services" → results.map(_.location.connection.name)))
    Future.traverse(results)(_.unregister()).map { _ =>
      log.info(s"Services are unregistered")
      Done
    }
  }

  private def shutdown(reason: Reason) = Await.result(coordinatedShutdown.run(reason), 10.seconds)
}
