package csw.framework.internal.supervisor

import akka.actor.typed.ActorRef
import csw.command.client.messages.CommandMessage
import csw.command.client.models.framework.LockingResponse
import csw.command.client.models.framework.LockingResponses._
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.LoggerFactory
import csw.params.core.models.Prefix

private[framework] class LockManager(val lockPrefix: Option[Prefix],
                                     adminPrefix: => Option[Prefix],
                                     loggerFactory: LoggerFactory) {
  private val log: Logger = loggerFactory.getLogger

  def lockComponent(source: Prefix, replyTo: ActorRef[LockingResponse])(startTimer: ⇒ Unit): LockManager = lockPrefix match {
    case None                ⇒ onAcquiringLock(source, replyTo, startTimer)
    case Some(`source`)      ⇒ onReAcquiringLock(source, replyTo, startTimer)
    case Some(currentPrefix) ⇒ onAcquiringFailed(replyTo, source, currentPrefix)
  }

  def unlockComponent(source: Prefix, replyTo: ActorRef[LockingResponse])(stopTimer: ⇒ Unit): LockManager = {
    (lockPrefix, adminPrefix) match {
      case (Some(`source`), _) | (_, Some(`source`)) ⇒ onLockReleased(source, replyTo, stopTimer)
      case (Some(currentPrefix), _)                  ⇒ onLockReleaseFailed(replyTo, source, currentPrefix)
      case (None, _)                                 ⇒ onLockAlreadyReleased(source, replyTo)
    }
  }

  def releaseLockOnTimeout(): LockManager = new LockManager(None, adminPrefix, loggerFactory)

  // Checks to see if component is locked, and if so, does the incoming prefix match the locked prefix
  def allowCommand(msg: CommandMessage): Boolean = lockPrefix match {
    case None ⇒ true
    case Some(currentPrefix) ⇒
      msg.command.source match {
        case `currentPrefix` ⇒
          log.info(s"Forwarding message ${msg.toString} to TLA for component: $currentPrefix")
          true
        case _ ⇒
          log.error(s"Cannot process the command [${msg.command.toString}] as the lock is acquired by component: $currentPrefix")
          false
      }
  }

  def isLocked: Boolean = lockPrefix.isDefined

  def isUnLocked: Boolean = lockPrefix.isEmpty

  private def onAcquiringLock(source: Prefix, replyTo: ActorRef[LockingResponse], startTimer: ⇒ Unit): LockManager = {
    log.info(s"The lock is successfully acquired by component: $source")
    replyTo ! LockAcquired
    startTimer
    new LockManager(Some(source), adminPrefix, loggerFactory)
  }

  private def onReAcquiringLock(source: Prefix, replyTo: ActorRef[LockingResponse], startTimer: ⇒ Unit): LockManager = {
    log.info(s"The lock is re-acquired by component: $source")
    replyTo ! LockAcquired
    startTimer
    this
  }

  private def onAcquiringFailed(replyTo: ActorRef[LockingResponse], source: Prefix, currentPrefix: Prefix) = {
    val failureReason =
      s"Invalid source ${source.prefix} for acquiring lock. Currently it is acquired by component: ${currentPrefix.prefix}"
    log.error(failureReason)
    replyTo ! AcquiringLockFailed(failureReason)
    this
  }

  private def onLockReleased(source: Prefix, replyTo: ActorRef[LockingResponse], stopTimer: ⇒ Unit): LockManager = {
    log.info(s"The lock is successfully released by component: ${source.prefix}")
    replyTo ! LockReleased
    stopTimer
    new LockManager(None, adminPrefix, loggerFactory)
  }

  private def onLockReleaseFailed(replyTo: ActorRef[LockingResponse], source: Prefix, currentPrefix: Prefix) = {
    val failureReason =
      s"Invalid source ${source.prefix} for releasing lock. Currently it is acquired by component: ${currentPrefix.prefix}"
    log.error(failureReason)
    replyTo ! ReleasingLockFailed(failureReason)
    this
  }

  private def onLockAlreadyReleased(source: Prefix, replyTo: ActorRef[LockingResponse]): LockManager = {
    log.warn(s"Cannot release lock for ${source.prefix} as it is already released")
    replyTo ! LockAlreadyReleased
    this
  }
}
