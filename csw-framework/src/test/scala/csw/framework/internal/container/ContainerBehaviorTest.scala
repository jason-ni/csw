package csw.framework.internal.container

import akka.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.typed.testkit.scaladsl.TestProbe
import akka.typed.testkit.{StubbedActorContext, TestKitSettings}
import akka.typed.{ActorRef, ActorSystem}
import akka.{Done, actor}
import csw.framework.ComponentInfos._
import csw.framework.internal.pubsub.PubSubBehaviorFactory
import csw.framework.internal.supervisor.{SupervisorBehaviorFactory, SupervisorInfoFactory, SupervisorLifecycleState}
import csw.framework.models.ContainerCommonMessage.{GetComponents, RegistrationComplete}
import csw.framework.models.ContainerIdleMessage.SupervisorsCreated
import csw.framework.models.FromSupervisorMessage.SupervisorLifecycleStateChanged
import csw.framework.models.RunningMessage.Lifecycle
import csw.framework.models.ToComponentLifecycleMessage.{GoOffline, GoOnline}
import csw.framework.models.{SupervisorInfo, _}
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, RegistrationResult}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, RegistrationFactory}
import csw.services.logging.scaladsl.{ComponentLogger, Logger}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.{Future, Promise}
import scala.util.Success

//DEOPSCSW-182-Control Life Cycle of Components
//DEOPSCSW-216-Locate and connect components to send AKKA commands
class ContainerBehaviorTest extends FunSuite with Matchers with MockitoSugar {
  implicit val untypedSystem: actor.ActorSystem  = ActorSystemFactory.remote()
  implicit val typedSystem: ActorSystem[Nothing] = untypedSystem.toTyped
  implicit val settings: TestKitSettings         = TestKitSettings(typedSystem)
  trait TypedActorMock[T] { this: ComponentLogger.TypedActor[T] ⇒
    override protected lazy val log: Logger = mock[Logger]
  }

  class IdleContainer() {
    val ctx                                                  = new StubbedActorContext[ContainerMessage]("test-container", 100, typedSystem)
    val supervisorFactory: SupervisorInfoFactory             = mock[SupervisorInfoFactory]
    val akkaRegistration                                     = AkkaRegistration(mock[AkkaConnection], TestProbe("test-probe").testActor)
    val locationService: LocationService                     = mock[LocationService]
    val registrationResult: RegistrationResult               = mock[RegistrationResult]
    private val pubSubBehaviorFactory: PubSubBehaviorFactory = mock[PubSubBehaviorFactory]
    var supervisorInfos: Set[SupervisorInfo]                 = Set.empty
    val answer = new Answer[Future[Option[SupervisorInfo]]] {
      override def answer(invocation: InvocationOnMock): Future[Option[SupervisorInfo]] = {
        val componentInfo = invocation.getArgument[ComponentInfo](1)
        val supervisorBehaviorFactory = SupervisorBehaviorFactory.make(
          Some(ctx.self),
          componentInfo,
          locationService,
          registrationFactory,
          pubSubBehaviorFactory
        )
        val supervisorInfo = SupervisorInfo(
          untypedSystem,
          Component(
            ctx.spawn(
              supervisorBehaviorFactory,
              componentInfo.name
            ),
            componentInfo
          )
        )
        supervisorInfos += supervisorInfo
        Future.successful(Some(supervisorInfo))
      }
    }

    when(
      supervisorFactory
        .make(ArgumentMatchers.any[ActorRef[ContainerIdleMessage]],
              ArgumentMatchers.any[ComponentInfo],
              ArgumentMatchers.any[LocationService])
    ).thenAnswer(answer)

    private val registrationFactory: RegistrationFactory = mock[RegistrationFactory]
    when(registrationFactory.akkaTyped(ArgumentMatchers.any[AkkaConnection], ArgumentMatchers.any[ActorRef[_]]))
      .thenReturn(akkaRegistration)

    private val eventualRegistrationResult: Future[RegistrationResult] =
      Promise[RegistrationResult].complete(Success(registrationResult)).future
    private val eventualDone: Future[Done] = Promise[Done].complete(Success(Done)).future

    when(locationService.register(akkaRegistration)).thenReturn(eventualRegistrationResult)
    when(registrationResult.unregister()).thenReturn(eventualDone)

    val containerBehavior =
      new ContainerBehavior(ctx, containerInfo, supervisorFactory, registrationFactory, locationService)
      with TypedActorMock[ContainerMessage]

  }

  class RunningContainer() extends IdleContainer {
    containerBehavior.onMessage(SupervisorsCreated(supervisorInfos))
    ctx.children.map(
      child ⇒
        containerBehavior.onMessage(SupervisorLifecycleStateChanged(child.upcast, SupervisorLifecycleState.Running))
    )

    ctx.children
      .map(child ⇒ ctx.childInbox(child.upcast))
      .map(_.receiveAll())
  }

  test("should start in initialize lifecycle state and should not accept any outside messages") {
    val idleContainer = new IdleContainer
    import idleContainer._

    verify(locationService).register(akkaRegistration)
    containerBehavior.lifecycleState shouldBe ContainerLifecycleState.Idle
    containerBehavior.onMessage(RegistrationComplete(registrationResult))
    containerBehavior.registrationOpt.get shouldBe registrationResult
  }

  test("should change its lifecycle state to running after all components move to running lifecycle state") {
    val idleContainer = new IdleContainer
    import idleContainer._

    verify(locationService).register(akkaRegistration)
    ctx.selfInbox.receiveMsg() shouldBe a[RegistrationComplete]
    // supervisor per component
    ctx.children.size shouldBe containerInfo.components.size
    ctx.selfInbox.receiveMsg() shouldBe a[SupervisorsCreated]
    containerBehavior.onMessage(SupervisorsCreated(supervisorInfos))
    containerBehavior.supervisors.size shouldBe 2
    containerBehavior.supervisors.map(_.component.info) shouldBe containerInfo.components

    // simulate that container receives LifecycleStateChanged to Running message from all components
    ctx.children.map(
      child ⇒
        containerBehavior.onMessage(SupervisorLifecycleStateChanged(child.upcast, SupervisorLifecycleState.Running))
    )

    verify(locationService).register(akkaRegistration)

    containerBehavior.onMessage(RegistrationComplete(registrationResult))

    containerBehavior.lifecycleState shouldBe ContainerLifecycleState.Running
  }

  test("should handle restart message by changing its lifecycle state to initialize") {
    val runningContainer = new RunningContainer
    import runningContainer._

    containerBehavior.onMessage(Restart)
    containerBehavior.runningComponents shouldBe Set.empty
    containerBehavior.lifecycleState shouldBe ContainerLifecycleState.Idle

    containerInfo.components.toList
      .map(component ⇒ ctx.childInbox[SupervisorExternalMessage](component.name))
      .map(_.receiveMsg()) should contain only Restart
  }

  test("should change its lifecycle state from restarting to running after all components have restarted") {
    val runningContainer = new RunningContainer
    import runningContainer._

    containerBehavior.onMessage(Restart)

    containerInfo.components.toList
      .map(component ⇒ ctx.childInbox(component.name))
      .map(_.receiveAll())

    ctx.children.map(
      child ⇒
        containerBehavior.onMessage(SupervisorLifecycleStateChanged(child.upcast, SupervisorLifecycleState.Running))
    )

    containerBehavior.lifecycleState shouldBe ContainerLifecycleState.Running
  }

  test("should handle GoOnline and GoOffline Lifecycle messages by forwarding to all components") {
    val runningContainer = new RunningContainer
    import runningContainer._

    val initialLifecycleState = containerBehavior.lifecycleState

    containerBehavior.onMessage(Lifecycle(GoOnline))
    containerInfo.components.toList
      .map(component ⇒ ctx.childInbox[SupervisorExternalMessage](component.name))
      .map(_.receiveMsg()) should contain only Lifecycle(GoOnline)

    initialLifecycleState shouldBe containerBehavior.lifecycleState

    containerBehavior.onMessage(Lifecycle(GoOffline))
    containerInfo.components.toList
      .map(component ⇒ ctx.childInbox[SupervisorExternalMessage](component.name))
      .map(_.receiveMsg()) should contain only Lifecycle(GoOffline)

    initialLifecycleState shouldBe containerBehavior.lifecycleState
  }

  test("container should be able to handle GetAllComponents message by responding with list of all components") {
    val idleContainer = new IdleContainer
    import idleContainer._

    verify(locationService).register(akkaRegistration)
    // Container should handle GetComponents message in Idle lifecycle state
    containerBehavior.lifecycleState shouldBe ContainerLifecycleState.Idle
    val probe = TestProbe[Components]

    containerBehavior.onMessage(GetComponents(probe.ref))

    probe.expectMsg(Components(containerBehavior.supervisors.map(_.component)))

    // Container should handle GetComponents message in Running lifecycle state
    containerBehavior.onMessage(SupervisorsCreated(supervisorInfos))
    ctx.children.map(
      child ⇒
        containerBehavior.onMessage(SupervisorLifecycleStateChanged(child.upcast, SupervisorLifecycleState.Running))
    )

    ctx.children
      .map(child ⇒ ctx.childInbox(child.upcast))
      .map(_.receiveAll())

    containerBehavior.onMessage(RegistrationComplete(registrationResult))

    containerBehavior.lifecycleState shouldBe ContainerLifecycleState.Running

    containerBehavior.onMessage(GetComponents(probe.ref))

    probe.expectMsg(Components(containerBehavior.supervisors.map(_.component)))
  }
}