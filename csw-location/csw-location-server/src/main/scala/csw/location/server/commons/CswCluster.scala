package csw.location.server.commons

import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.{ActorSystem, CoordinatedShutdown, Scheduler}
import akka.cluster.ddata.SelfUniqueAddress
import akka.cluster.ddata.typed.scaladsl
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.http.management.ClusterHttpManagement
import akka.cluster.typed.{Cluster, Join}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.Timeout
import akka.{actor, Done}
import csw.location.api.exceptions.CouldNotJoinCluster
import csw.location.server.commons.ClusterConfirmationMessages.{HasJoinedCluster, Shutdown}
import csw.location.server.commons.CoordinatedShutdownReasons.FailureReason
import csw.logging.api.scaladsl.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * CswCluster provides cluster properties to manage distributed data. It is created when location service instance is created
 * in `csw-framework` and joins the cluster.
 *
 * @note it is highly recommended that explicit creation of CswCluster should be for advanced usages or testing purposes only
 */
class CswCluster private (_actorSystem: ActorSystem) {

  private val log: Logger = LocationServiceLogger.getLogger

  /**
   * Identifies the hostname where ActorSystem is running
   */
  val hostname: String = _actorSystem.settings.config.getString("akka.remote.netty.tcp.hostname")

  implicit val actorSystem: ActorSystem                = _actorSystem
  implicit val typedSystem: actor.typed.ActorSystem[_] = _actorSystem.toTyped
  implicit val scheduler: Scheduler                    = typedSystem.scheduler
  implicit val ec: ExecutionContext                    = actorSystem.dispatcher
  implicit val mat: ActorMaterializer                  = makeMat()
  implicit val cluster: Cluster                        = Cluster(typedSystem)
  private val distributedData: DistributedData         = scaladsl.DistributedData(typedSystem)
  implicit val node: SelfUniqueAddress                 = distributedData.selfUniqueAddress

  /**
   * Gives the replicator for the current ActorSystem
   */
  private[location] val replicator: ActorRef[Replicator.Command] = distributedData.replicator

  /**
   * Gives handle to CoordinatedShutdown extension
   */
  val coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(actorSystem)

  /**
   * Creates an ActorMaterializer for current ActorSystem
   */
  private def makeMat(): ActorMaterializer = ActorMaterializer()

  /**
   * If `startManagement` flag is set to true (which is true only when a managementPort is defined in ClusterSettings)
   * then an akka provided HTTP service is started at provided port. It provides services related to akka cluster management e.g see the members of the cluster and their status i.e. up or weakly up etc.
   * Currently, cluster management service is started on `csw-location-server` which may help in production to monitor
   * cluster status. But, it can be started on any machine that is a part of akka cluster.
   */
  // $COVERAGE-OFF$
  private def startClusterManagement(): Unit = {
    val startManagement = actorSystem.settings.config.getBoolean("startManagement")
    if (startManagement) {
      val clusterHttpManagement = ClusterHttpManagement(akka.cluster.Cluster(actorSystem))
      Await.result(clusterHttpManagement.start(), 10.seconds)
    }
  }
  // $COVERAGE-ON$

  // When new member tries to join the cluster, location service makes sure that member is weakly up or up before returning handle to location service
  private def joinCluster(): Done = {
    // Check if seed nodes are provided to join csw-cluster
    val emptySeeds = actorSystem.settings.config.getStringList("akka.cluster.seed-nodes").isEmpty
    if (emptySeeds) {
      // If no seeds are provided (which happens only during testing), then create a single node cluster by joining to self
      cluster.manager ! Join(cluster.selfMember.address)
    }

    val confirmationActorF: ActorRef[Any] = actorSystem.spawn(ClusterConfirmationActor.behavior(), "ClusterConfirmationActor")
    implicit val timeout: Timeout         = Timeout(5.seconds)
    def statusF: Future[Option[Done]]     = confirmationActorF ? HasJoinedCluster
    def status: Option[Done]              = Await.result(statusF, 5.seconds)
    val success                           = BlockingUtils.poll(status.isDefined, 20.seconds)
    if (!success) {
      log.error(CouldNotJoinCluster.getMessage, ex = CouldNotJoinCluster)
      throw CouldNotJoinCluster
    }
    confirmationActorF ! Shutdown
    Done
  }

  /**
   * Terminates the ActorSystem and gracefully leaves the cluster
   */
  def shutdown(reason: Reason): Future[Done] = coordinatedShutdown.run(reason)
}

/**
 * Manages initialization and termination of ActorSystem and the Cluster.
 *
 * @note the creation of CswCluster will be blocked till the ActorSystem joins csw-cluster successfully
 */
object CswCluster {

  private val log: Logger = LocationServiceLogger.getLogger

  /**
   * Creates CswCluster with the default cluster settings
   *
   * @see [[ClusterSettings]] same as [[csw.location.server.commons.CswCluster.withSettings()]]
   * @return an instance of CswCluster
   */
  def make(): CswCluster = withSettings(ClusterSettings())

  /**
   * Creates CswCluster with the given customized settings
   *
   * @return an instance of CswCluster
   */
  def withSettings(settings: ClusterSettings): CswCluster = withSystem(settings.system)

  /**
   * Creates CswCluster with the given ActorSystem
   *
   * @return an instance of CswCluster
   */
  def withSystem(actorSystem: ActorSystem): CswCluster = {
    val cswCluster = new CswCluster(actorSystem)
    try {
      cswCluster.startClusterManagement()
      cswCluster.joinCluster()
      cswCluster
    } catch {
      case NonFatal(ex) ⇒
        Await.result(cswCluster.shutdown(FailureReason(ex)), 10.seconds)
        log.error(ex.getMessage, ex = ex)
        throw ex
    }
  }
}
