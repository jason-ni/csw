package csw.config.client.scaladsl

import akka.actor.ActorSystem
import csw.location.api.scaladsl.LocationService
import csw.config.api.scaladsl.{ConfigClientService, ConfigService}
import csw.config.client.internal.{ActorRuntime, ConfigClient, ConfigServiceResolver}

/**
 * The factory is used to create ConfigClient instance.
 */
object ConfigClientFactory {

  /**
   * Create ConfigClient instance for admin users.
   *
   * @param actorSystem local actor system of the client
   * @param locationService location service instance which will be used to resolve the location of config server
   * @return an instance of ConfigService
   */
  def adminApi(actorSystem: ActorSystem, locationService: LocationService): ConfigService = {
    val actorRuntime          = new ActorRuntime(actorSystem)
    val configServiceResolver = new ConfigServiceResolver(locationService, actorRuntime)
    new ConfigClient(configServiceResolver, actorRuntime)
  }

  /**
   * Create ConfigClient instance for non admin users.
   *
   * @param actorSystem local actor system of the client
   * @param locationService location service instance which will be used to resolve the location of config server
   * @return an instance of ConfigClientService
   */
  def clientApi(actorSystem: ActorSystem, locationService: LocationService): ConfigClientService =
    adminApi(actorSystem, locationService)
}