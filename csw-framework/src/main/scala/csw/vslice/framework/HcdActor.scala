package csw.vslice.framework

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.param.Parameters.Setup
import csw.param.StateVariable.CurrentState
import csw.vslice.framework.FromComponentLifecycleMessage.{Initialized, Running}
import csw.vslice.framework.HcdActor.Mode
import csw.vslice.framework.InitialHcdMsg.{Run, ShutdownComplete}
import csw.vslice.framework.RunningHcdMsg._

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.reflect.ClassTag

object HcdActor {
  sealed trait Mode
  object Mode {
    case object Initial extends Mode
    case object Running extends Mode
  }
}

abstract class HcdActor[Msg <: DomainMsg: ClassTag](ctx: ActorContext[HcdMsg],
                                                    supervisor: ActorRef[FromComponentLifecycleMessage])
    extends Actor.MutableBehavior[HcdMsg] {

  val domainAdapter: ActorRef[Msg] = ctx.spawnAdapter(DomainHcdMsg.apply)

  val pubSubRef: ActorRef[PubSub[CurrentState]] = ctx.spawnAnonymous(PubSubActor.behaviour[CurrentState])

  import ctx.executionContext

  var context: Mode = _

  def initialize(): Future[Unit]
  def onRun(): Unit
  def onShutdown(): Unit
  def onShutdownComplete(): Unit
  def onLifecycle(x: ToComponentLifecycleMessage): Unit
  def onSetup(sc: Setup): Unit
  def onDomainMsg(msg: Msg): Unit

  async {
    await(initialize())
    supervisor ! Initialized(ctx.self, pubSubRef)
    context = Mode.Initial
  }

  override def onMessage(msg: HcdMsg): Behavior[HcdMsg] = {
    (context, msg) match {
      case (Mode.Initial, x: InitialHcdMsg) ⇒ handleInitial(x)
      case (Mode.Running, x: RunningHcdMsg) ⇒ handleRunning(x)
      case _                                ⇒ println(s"current context=$context does not handle message=$msg")
    }
    this
  }

  private def handleInitial(x: InitialHcdMsg): Unit = x match {
    case Run(replyTo) =>
      onRun()
      context = Mode.Running
      replyTo ! Running(ctx.self, pubSubRef)
    case ShutdownComplete =>
      onShutdown()
  }

  private def handleRunning(x: RunningHcdMsg): Unit = x match {
    case ShutdownComplete     => onShutdownComplete()
    case Lifecycle(message)   => onLifecycle(message)
    case Submit(command)      => onSetup(command)
    case DomainHcdMsg(y: Msg) ⇒ onDomainMsg(y)
    case DomainHcdMsg(y)      ⇒ println(s"unhandled domain msg: $y")
  }
}
