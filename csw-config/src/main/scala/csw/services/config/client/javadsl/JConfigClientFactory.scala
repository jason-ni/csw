package csw.services.config.client.javadsl

import akka.actor.ActorSystem
import csw.services.config.api.javadsl.IConfigService
import csw.services.config.client.internal.{ActorRuntime, JConfigService}
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.location.javadsl.ILocationService

object JConfigClientFactory {

  def make(actorSystem: ActorSystem, locationService: ILocationService): IConfigService = {
    val configService = ConfigClientFactory.make(actorSystem, locationService.asScala)
    val actorRuntime = new ActorRuntime(actorSystem)
    new JConfigService(configService, actorRuntime)
  }

}
