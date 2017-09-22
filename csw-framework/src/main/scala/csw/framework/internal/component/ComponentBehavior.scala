package csw.framework.internal.component

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior, PostStop, Signal}
import csw.ccs.CommandStatus
import csw.framework.models.CommandMessage.{Oneway, Submit}
import csw.framework.models.CommonMessage.UnderlyingHookFailed
import csw.framework.models.FromComponentLifecycleMessage.{Initialized, Running}
import csw.framework.models.IdleMessage.Initialize
import csw.framework.models.InitialMessage.Run
import csw.framework.models.RunningMessage.{DomainMessage, Lifecycle}
import csw.framework.models.ToComponentLifecycleMessage.{GoOffline, GoOnline}
import csw.framework.models.{RunningMessage, _}
import csw.framework.scaladsl.ComponentHandlers
import csw.services.logging.scaladsl.ComponentLogger

import scala.async.Async.{async, await}
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.Try

object ComponentBehavior {
  val shutdownTimeout: FiniteDuration = 10.seconds
}

class ComponentBehavior[Msg <: DomainMessage: ClassTag](
    ctx: ActorContext[ComponentMessage],
    componentName: String,
    supervisor: ActorRef[FromComponentLifecycleMessage],
    lifecycleHandlers: ComponentHandlers[Msg]
) extends ComponentLogger.TypedActor[ComponentMessage](ctx, Some(componentName)) {

  implicit val ec: ExecutionContext = ctx.executionContext

  var lifecycleState: ComponentLifecycleState = ComponentLifecycleState.Idle

  ctx.self ! Initialize

  def onMessage(msg: ComponentMessage): Behavior[ComponentMessage] = {
    log.debug(s"Component TLA in lifecycle state :[$lifecycleState] received message :[$msg]")
    (lifecycleState, msg) match {
      case (_, msg: CommonMessage)                                    ⇒ onCommon(msg)
      case (ComponentLifecycleState.Idle, msg: IdleMessage)           ⇒ onIdle(msg)
      case (ComponentLifecycleState.Initialized, msg: InitialMessage) ⇒ onInitial(msg)
      case (ComponentLifecycleState.Running, msg: RunningMessage)     ⇒ onRun(msg)
      case _                                                          ⇒ log.error(s"Unexpected message :[$msg] received by component in lifecycle state :[$lifecycleState]")
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[ComponentMessage]] = {
    case PostStop ⇒
      log.warn("Component TLA is shutting down")
      val shutdownResult = Try {
        log.info("Invoking lifecycle handler's onShutdown hook")
        Await.result(lifecycleHandlers.onShutdown(), ComponentBehavior.shutdownTimeout)
      }
      //log exception if onShutdown handler fails and proceed with `Shutdown` or `Restart`
      shutdownResult.failed.foreach(throwable ⇒ log.error(throwable.getMessage, ex = throwable))
      this
  }

  private def onCommon(msg: CommonMessage): Unit = {
    msg match {
      case UnderlyingHookFailed(exception) ⇒
        log.error(exception.getMessage, ex = exception)
        throw exception
    }
  }

  private def onIdle(x: IdleMessage): Unit = x match {
    case Initialize ⇒
      async {
        log.info("Invoking lifecycle handler's initialize hook")
        await(lifecycleHandlers.initialize())
        log.debug(
          s"Component TLA is changing lifecycle state from [$lifecycleState] to [${ComponentLifecycleState.Initialized}]"
        )
        lifecycleState = ComponentLifecycleState.Initialized
        supervisor ! Initialized(ctx.self)
      }.failed.foreach(throwable ⇒ ctx.self ! UnderlyingHookFailed(throwable))
  }

  private def onInitial(x: InitialMessage): Unit = x match {
    case Run ⇒
      async {
        log.info("Invoking lifecycle handler's onRun hook")
        await(lifecycleHandlers.onRun())
        log.debug(
          s"Component TLA is changing lifecycle state from [$lifecycleState] to [${ComponentLifecycleState.Running}]"
        )
        lifecycleState = ComponentLifecycleState.Running
        lifecycleHandlers.isOnline = true
        supervisor ! Running(ctx.self)
      }.failed.foreach(throwable ⇒ ctx.self ! UnderlyingHookFailed(throwable))
  }

  private def onRun(runningMessage: RunningMessage): Unit = runningMessage match {
    case Lifecycle(message) ⇒ onLifecycle(message)
    case x: Msg ⇒
      log.info(s"Invoking lifecycle handler's onDomainMsg hook with msg :[$x]")
      lifecycleHandlers.onDomainMsg(x)
    case x: CommandMessage ⇒ onRunningCompCommandMessage(x)
    case msg               ⇒ log.error(s"Component TLA cannot handle message :[$msg]")
  }

  private def onLifecycle(message: ToComponentLifecycleMessage): Unit = message match {
    case GoOnline ⇒
      if (!lifecycleHandlers.isOnline) {
        lifecycleHandlers.isOnline = true
        log.info("Invoking lifecycle handler's onGoOnline hook")
        lifecycleHandlers.onGoOnline()
        log.debug(s"Component TLA is Online")
      }
    case GoOffline ⇒
      if (lifecycleHandlers.isOnline) {
        lifecycleHandlers.isOnline = false
        log.info("Invoking lifecycle handler's onGoOffline hook")
        lifecycleHandlers.onGoOffline()
        log.debug(s"Component TLA is Offline")
      }
  }

  def onRunningCompCommandMessage(message: CommandMessage): Unit = {
    val newMessage: CommandMessage = message match {
      case x: Oneway ⇒ x.copy(replyTo = ctx.spawnAnonymous(Actor.ignore))
      case x: Submit ⇒ x
    }
    log.info(s"Invoking lifecycle handler's onControlCommand hook with msg :[$newMessage]")
    val validation              = lifecycleHandlers.onControlCommand(newMessage)
    val validationCommandResult = CommandStatus.validationAsCommandStatus(validation)
    message.replyTo ! validationCommandResult
  }

}