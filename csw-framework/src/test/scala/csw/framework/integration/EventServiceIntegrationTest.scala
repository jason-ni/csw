package csw.framework.integration

import akka.actor.testkit.typed.scaladsl.{TestInbox, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.common.FrameworkAssertions
import csw.common.components.framework.SampleComponentState._
import csw.commons.redis.EmbeddedRedis
import csw.framework.internal.wiring.{Container, FrameworkWiring}
import csw.messages.commands
import csw.messages.commands.CommandName
import csw.messages.framework.ContainerLifecycleState
import csw.messages.location.ComponentId
import csw.messages.location.ComponentType.{Assembly, HCD}
import csw.messages.location.Connection.AkkaConnection
import csw.messages.params.states.{CurrentState, StateName}
import csw.services.command.scaladsl.CommandService
import csw.services.event.helpers.TestFutureExt.RichFuture
import csw.services.event.internal.commons.EventServiceConnection
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.duration.DurationLong

//DEOPSCSW-395: Provide EventService handle to component developers
class EventServiceIntegrationTest extends FunSuite with EmbeddedRedis with Matchers with BeforeAndAfterAll {
  private val testWiring = new FrameworkTestWiring()
  import testWiring._

  private val masterId: String      = ConfigFactory.load().getString("redis.masterId")
  private val (_, sentinel, server) = startSentinelAndRegisterService(EventServiceConnection.value, masterId)

  private val filterAssemblyConnection = AkkaConnection(ComponentId("Filter", Assembly))
  private val disperserHcdConnection   = AkkaConnection(ComponentId("Disperser", HCD))

  override protected def afterAll(): Unit = {
    shutdown()
    stopSentinel(sentinel, server)
  }

  test("should be able to publish and subscribe to events") {
    val wiring: FrameworkWiring = FrameworkWiring.make(testActorSystem)
    val containerRef            = Container.spawn(ConfigFactory.load("container_tracking_connections.conf"), wiring).await

    val assemblyProbe                = TestInbox[CurrentState]()
    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]("container-lifecycle-state-probe")
    FrameworkAssertions.assertThatContainerIsRunning(containerRef, containerLifecycleStateProbe, 5.seconds)

    val filterAssemblyLocation = wiring.locationService.find(filterAssemblyConnection).await
    val disperserHcdLocation   = wiring.locationService.find(disperserHcdConnection).await

    val assemblyCommandService  = new CommandService(filterAssemblyLocation.get)
    val disperserCommandService = new CommandService(disperserHcdLocation.get)

    assemblyCommandService.subscribeCurrentState(assemblyProbe.ref ! _)

    implicit val timeout: Timeout = Timeout(100.millis)
    assemblyCommandService.submit(commands.Setup(prefix, CommandName("subscribe.event.success"), None))
    Thread.sleep(1000)
    disperserCommandService.submit(commands.Setup(prefix, CommandName("publish.event.success"), None))
    Thread.sleep(1000)

    val states = assemblyProbe.receiveAll().filter(_.paramSet.contains(choiceKey.set(eventReceivedChoice)))

    states.size shouldBe 2 //inclusive of latest event
    states should contain(CurrentState(prefix, StateName("testStateName"), Set(choiceKey.set(eventReceivedChoice))))
  }
}
