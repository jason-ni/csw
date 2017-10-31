package csw.trombone.assembly.commands

import akka.typed.ActorRef
import akka.typed.scaladsl.{Actor, ActorContext}
import csw.messages.CommandExecutionResponse.{Completed, Error, NoLongerValid}
import csw.messages.CommandMessage.Submit
import csw.messages._
import csw.messages.ccs.CommandIssue.WrongInternalStateIssue
import csw.messages.ccs.commands.Setup
import csw.messages.params.models.RunId
import csw.messages.params.models.Units.encoder
import csw.trombone.assembly._
import csw.trombone.assembly.actors.TromboneState.TromboneState
import csw.trombone.hcd.TromboneHcdState

import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble

class SetElevationCommand(ctx: ActorContext[AssemblyCommandHandlerMsgs],
                          ac: AssemblyContext,
                          s: Setup,
                          tromboneHCD: Option[ActorRef[SupervisorExternalMessage]],
                          startState: TromboneState,
                          stateActor: ActorRef[PubSub[AssemblyState]])
    extends AssemblyCommand(ctx, startState, stateActor) {

  import TromboneHcdState._
  import csw.trombone.assembly.actors.TromboneState._
  import ctx.executionContext

  def startCommand(): Future[CommandExecutionResponse] = {
    if (startState.cmdChoice == cmdUninitialized || startState.moveChoice != moveIndexed && startState.moveChoice != moveMoving) {
      Future(
        NoLongerValid(
          WrongInternalStateIssue(
            s"Assembly state of ${startState.cmdChoice}/${startState.moveChoice} does not allow datum"
          )
        )
      )
    } else {
      val elevationItem   = s(ac.naElevationKey)
      val stagePosition   = Algorithms.rangeDistanceToStagePosition(elevationItem.head)
      val encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition)

      println(
        s"Using elevation as rangeDistance: ${elevationItem.head} to get stagePosition: $stagePosition to encoder: $encoderPosition"
      )

      val stateMatcher = Matchers.posMatcher(encoderPosition)
      val scOut        = Setup(ac.obsId, axisMoveCK).add(positionKey -> encoderPosition withUnits encoder)

      publishState(TromboneState(cmdItem(cmdBusy), moveItem(moveIndexing), startState.sodiumLayer, startState.nss))
      tromboneHCD.foreach(_ ! Submit(scOut, ctx.spawnAnonymous(Actor.ignore)))
      matchCompletion(stateMatcher, tromboneHCD.get, 5.seconds) {
        case Completed() =>
          publishState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
          Completed()
        case Error(message) =>
          println(s"Data command match failed with error: $message")
          Error(message)
        case _ ⇒ Error("")
      }
    }
  }

  def stopCommand(): Unit = {
    tromboneHCD.foreach(_ ! Submit(TromboneHcdState.cancelSC(RunId(), s.obsId), ctx.spawnAnonymous(Actor.ignore)))
  }

}
