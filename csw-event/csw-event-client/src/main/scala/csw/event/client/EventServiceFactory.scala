package csw.event.client

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import csw.event.api.javadsl.IEventService
import csw.event.api.scaladsl.EventService
import csw.event.client.internal.commons.EventStreamSupervisionStrategy
import csw.event.client.internal.commons.javawrappers.JEventService
import csw.event.client.internal.commons.serviceresolver.{
  EventServiceHostPortResolver,
  EventServiceLocationResolver,
  EventServiceResolver
}
import csw.event.client.internal.kafka.KafkaEventService
import csw.event.client.internal.redis.RedisEventService
import csw.event.client.models.EventStore
import csw.event.client.models.EventStores.{KafkaStore, RedisStore}
import csw.location.api.javadsl.ILocationService
import csw.location.api.scaladsl.LocationService

import scala.concurrent.ExecutionContext

/**
 * Factory to create EventService
 */
class EventServiceFactory(store: EventStore = RedisStore()) {

  /**
   * A java helper to construct EventServiceFactory
   */
  def this() = this(RedisStore())

  /**
   * API to create [[EventService]] using [[LocationService]] to resolve Event Server.
   *
   * @param locationService instance of location service
   * @param system an actor system required for underlying event streams
   * @return [[EventService]] which provides handles to [[csw.event.api.scaladsl.EventPublisher]] and [[csw.event.api.scaladsl.EventSubscriber]]
   */
  def make(locationService: LocationService)(implicit system: ActorSystem): EventService =
    eventService(new EventServiceLocationResolver(locationService)(system.dispatcher))

  /**
   * API to create [[EventService]] using host and port of Event Server.
   *
   * @param host hostname of event server
   * @param port port on which event server is running
   * @param system an actor system required for underlying event streams
   * @return [[EventService]] which provides handles to [[csw.event.api.scaladsl.EventPublisher]] and [[csw.event.api.scaladsl.EventSubscriber]]
   */
  def make(host: String, port: Int)(implicit system: ActorSystem): EventService =
    eventService(new EventServiceHostPortResolver(host, port))

  /**
   * Java API to create [[IEventService]] using [[ILocationService]] to resolve Event Server.
   *
   * @param locationService instance of location service
   * @param actorSystem an actor system required for underlying event streams
   * @return [[IEventService]] which provides handles to [[csw.event.api.javadsl.IEventPublisher]] and [[csw.event.api.javadsl.IEventSubscriber]]
   */
  def jMake(locationService: ILocationService, actorSystem: ActorSystem): IEventService = {
    val eventService = make(locationService.asScala)(actorSystem)
    new JEventService(eventService)
  }

  /**
   * Java API to create [[IEventService]] using host and port of Event Server.
   *
   * @param host hostname of event server
   * @param port port on which event server is running
   * @param system an actor system required for underlying event streams
   * @return [[IEventService]] which provides handles to [[csw.event.api.javadsl.IEventPublisher]] and [[csw.event.api.javadsl.IEventSubscriber]]
   */
  def jMake(host: String, port: Int, system: ActorSystem): IEventService = {
    val eventService = make(host, port)(system)
    new JEventService(eventService)
  }

  private def mat()(implicit actorSystem: ActorSystem): Materializer =
    ActorMaterializer(ActorMaterializerSettings(actorSystem).withSupervisionStrategy(EventStreamSupervisionStrategy.decider))

  private def eventService(eventServiceResolver: EventServiceResolver)(implicit system: ActorSystem) = {
    implicit val ec: ExecutionContext       = system.dispatcher
    implicit val materializer: Materializer = mat()

    def masterId = system.settings.config.getString("csw-event.redis.masterId")

    store match {
      case RedisStore(client) ⇒ new RedisEventService(eventServiceResolver, masterId, client)
      case KafkaStore         ⇒ new KafkaEventService(eventServiceResolver)
    }
  }
}
