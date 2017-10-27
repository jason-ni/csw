package csw.common.components

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.framework.scaladsl.ComponentHandlers
import csw.messages.PubSub.{Publish, PublisherMessage}
import csw.messages._
import csw.messages.ccs.ValidationIssue.OtherIssue
import csw.messages.ccs.commands.{ControlCommand, Observe, Setup}
import csw.messages.ccs.{Validation, Validations}
import csw.messages.framework.ComponentInfo
import csw.messages.location.Connection.{AkkaConnection, HttpConnection, TcpConnection}
import csw.messages.location._
import csw.messages.params.generics.GChoiceKey
import csw.messages.params.generics.KeyType.ChoiceKey
import csw.messages.params.models.{Choice, Choices, Prefix}
import csw.messages.params.states.CurrentState
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.ComponentLogger

import scala.concurrent.{ExecutionContextExecutor, Future}

object SampleComponentState {
  val restartChoice             = Choice("Restart")
  val onlineChoice              = Choice("Online")
  val domainChoice              = Choice("Domain")
  val shutdownChoice            = Choice("Shutdown")
  val setupConfigChoice         = Choice("SetupConfig")
  val observeConfigChoice       = Choice("ObserveConfig")
  val submitCommandChoice       = Choice("SubmitCommand")
  val oneWayCommandChoice       = Choice("OneWayCommand")
  val initChoice                = Choice("Initialize")
  val offlineChoice             = Choice("Offline")
  val akkaLocationUpdatedChoice = Choice("LocationUpdated")
  val akkaLocationRemovedChoice = Choice("LocationRemoved")
  val httpLocationUpdatedChoice = Choice("HttpLocationUpdated")
  val httpLocationRemovedChoice = Choice("HttpLocationRemoved")
  val tcpLocationUpdatedChoice  = Choice("TcpLocationUpdated")
  val tcpLocationRemovedChoice  = Choice("TcpLocationRemoved")
  val prefix: Prefix            = Prefix("wfos.prog.cloudcover")
  val successPrefix: Prefix     = Prefix("wfos.prog.cloudcover.success")
  val failedPrefix: Prefix      = Prefix("wfos.prog.cloudcover.failure")

  val choices: Choices =
    Choices.fromChoices(
      restartChoice,
      onlineChoice,
      domainChoice,
      shutdownChoice,
      setupConfigChoice,
      observeConfigChoice,
      submitCommandChoice,
      oneWayCommandChoice,
      initChoice,
      offlineChoice,
      akkaLocationUpdatedChoice,
      akkaLocationRemovedChoice,
      httpLocationUpdatedChoice,
      httpLocationRemovedChoice,
      tcpLocationUpdatedChoice,
      tcpLocationRemovedChoice
    )
  val choiceKey: GChoiceKey          = ChoiceKey.make("choiceKey", choices)
  val httpConnection: HttpConnection = HttpConnection(ComponentId("exampleHTTPService", ComponentType.Service))
  val tcpConnection: TcpConnection   = TcpConnection(ComponentId("exampleTcpService", ComponentType.Service))
}

class SampleComponentHandlers(
    ctx: ActorContext[ComponentMessage],
    componentInfo: ComponentInfo,
    pubSubRef: ActorRef[PublisherMessage[CurrentState]],
    locationService: LocationService
) extends ComponentHandlers[ComponentDomainMessage](ctx, componentInfo, pubSubRef, locationService)
    with ComponentLogger.Simple {

  import SampleComponentState._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  override def initialize(): Future[ComponentHandlers[ComponentDomainMessage]] = {
    // DEOPSCSW-153: Accessibility of logging service to other CSW components
    log.info("Initializing Component TLA")
    Thread.sleep(100)
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(initChoice))))

    trackConnection(httpConnection)
    trackConnection(tcpConnection)
    Future.successful(this)
  }

  override def onGoOffline(): ComponentHandlers[ComponentDomainMessage] = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(offlineChoice))))
    this
  }

  override def onGoOnline(): ComponentHandlers[ComponentDomainMessage] = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(onlineChoice))))
    this
  }

  override def onDomainMsg(msg: ComponentDomainMessage): ComponentHandlers[ComponentDomainMessage] = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(domainChoice))))
    this
  }

  override def onSubmit(
      controlCommand: ControlCommand,
      replyTo: ActorRef[CommandResponse]
  ): (ComponentHandlers[ComponentDomainMessage], Validation) = {
    // Adding passed in parameter to see if data is transferred properly
    pubSubRef ! Publish(
      CurrentState(prefix, Set(choiceKey.set(submitCommandChoice)))
    )
    (this, validateCommand(controlCommand))
  }

  override def onOneway(controlCommand: ControlCommand): (ComponentHandlers[ComponentDomainMessage], Validation) = {
    // Adding passed in parameter to see if data is transferred properly
    pubSubRef ! Publish(
      CurrentState(prefix, Set(choiceKey.set(oneWayCommandChoice)))
    )
    (this, validateCommand(controlCommand))
  }

  private def validateCommand(command: ControlCommand) = {
    command match {
      case Setup(_, somePrefix, _) ⇒
        pubSubRef ! Publish(CurrentState(somePrefix, Set(choiceKey.set(setupConfigChoice), command.paramSet.head)))
      case Observe(_, somePrefix, _) ⇒
        pubSubRef ! Publish(CurrentState(somePrefix, Set(choiceKey.set(observeConfigChoice), command.paramSet.head)))
      case _ ⇒
    }

    if (command.prefix.prefix.contains("success")) {
      Validations.Valid
    } else {
      Validations.Invalid(OtherIssue("Testing: Received failure, will return Invalid."))
    }
  }

  override def onShutdown(): Future[Unit] = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(shutdownChoice))))
    Thread.sleep(100)
    Future.unit
  }

  override protected def componentName(): String = componentInfo.name

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = trackingEvent match {
    case LocationUpdated(location) ⇒
      location.connection match {
        case _: AkkaConnection =>
          pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(akkaLocationUpdatedChoice))))
        case _: HttpConnection =>
          pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(httpLocationUpdatedChoice))))
        case _: TcpConnection =>
          pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(tcpLocationUpdatedChoice))))
      }
    case LocationRemoved(connection) ⇒
      connection match {
        case _: AkkaConnection =>
          pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(akkaLocationRemovedChoice))))
        case _: HttpConnection =>
          pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(httpLocationRemovedChoice))))
        case _: TcpConnection =>
          pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(tcpLocationRemovedChoice))))
      }
  }
}
