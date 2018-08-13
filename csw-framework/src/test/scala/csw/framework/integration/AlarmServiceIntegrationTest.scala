package csw.framework.integration

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import csw.common.FrameworkAssertions.assertThatSupervisorIsRunning
import csw.common.components.framework.SampleComponentState._
import csw.commons.redis.EmbeddedRedis
import csw.framework.FrameworkTestWiring
import csw.framework.internal.wiring.{FrameworkWiring, Standalone}
import csw.messages.commands.Setup
import csw.messages.commons.CoordinatedShutdownReasons.TestFinishedReason
import csw.messages.framework.SupervisorLifecycleState
import csw.messages.location.ComponentId
import csw.messages.location.ComponentType.HCD
import csw.messages.location.Connection.AkkaConnection
import csw.services.alarm.client.internal.commons.AlarmServiceConnection
import csw.services.command.scaladsl.CommandService
import csw.services.event.helpers.TestFutureExt.RichFuture
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.duration.DurationLong

//DEOPSCSW-490: Alarm service integration with framework
class AlarmServiceIntegrationTest extends FunSuite with EmbeddedRedis with Matchers with BeforeAndAfterAll {
  private val testWiring = new FrameworkTestWiring()
  import testWiring._

  private val masterId: String      = ConfigFactory.load().getString("csw-alarm.redis.masterId")
  private val (_, sentinel, server) = startSentinelAndRegisterService(AlarmServiceConnection.value, masterId)

  private val wiring: FrameworkWiring = FrameworkWiring.make(testActorSystem)
  private val adminAlarmService       = wiring.alarmServiceFactory.makeAdminApi(wiring.locationService).await

  override protected def beforeAll(): Unit = {
    val config: Config = ConfigFactory.parseResources("valid-alarms.conf")
    adminAlarmService.initAlarms(config, reset = true).await
  }

  override protected def afterAll(): Unit = {
    wiring.actorRuntime.shutdown(TestFinishedReason).await
    shutdown()
    stopSentinel(sentinel, server)
  }

  test("component should be able to set severity of an alarm") {
    import wiring._
    Standalone.spawn(ConfigFactory.load("standalone.conf"), wiring)

    val supervisorLifecycleStateProbe = TestProbe[SupervisorLifecycleState]("supervisor-lifecycle-state-probe")
    val akkaConnection                = AkkaConnection(ComponentId("IFS_Detector", HCD))
    val location                      = locationService.resolve(akkaConnection, 5.seconds).await

    val supervisorRef = location.get.componentRef
    assertThatSupervisorIsRunning(supervisorRef, supervisorLifecycleStateProbe, 5.seconds)

    val commandService = new CommandService(location.get)

    implicit val timeout: Timeout = Timeout(1000.millis)
    commandService.submit(Setup(prefix, setSeverityCommand, None)).await
    Thread.sleep(1000)

    adminAlarmService.getCurrentSeverity(testAlarmKey).await shouldEqual testSeverity
  }
}