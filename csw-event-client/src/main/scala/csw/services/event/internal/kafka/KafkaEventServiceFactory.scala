package csw.services.event.internal.kafka

import akka.actor.ActorSystem
import akka.stream.Materializer
import csw.services.event.internal.commons.EventServiceFactory
import csw.services.event.internal.commons.serviceresolver.EventServiceResolver
import csw.services.event.scaladsl.EventService

import scala.concurrent.ExecutionContext

object KafkaEventServiceFactory extends EventServiceFactory {
  override protected def eventServiceImpl(
      eventServiceResolver: EventServiceResolver
  )(implicit actorSystem: ActorSystem, ec: ExecutionContext, mat: Materializer): EventService =
    new KafkaEventService(eventServiceResolver)
}