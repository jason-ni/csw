package csw.integtration.apps

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{Actor, ActorPath, ActorRef, ActorSystem, Props}
import akka.serialization.Serialization
import akka.stream.ActorMaterializer
import csw.integtration.common.TestFutureExtension.RichFuture
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaRegistration, ComponentId, ComponentType, RegistrationResult}
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.params.core.models.Prefix

object AssemblyApp {

  private implicit val actorSystem: ActorSystem = ActorSystemFactory.remote()
  private implicit val mat: ActorMaterializer   = ActorMaterializer()
  val assemblyActorRef: ActorRef                = actorSystem.actorOf(Props[AssemblyApp], "assembly")
  val componentId                               = ComponentId("assembly", ComponentType.Assembly)
  val connection                                = AkkaConnection(componentId)

  val actorPath: ActorPath                   = ActorPath.fromString(Serialization.serializedActorPath(assemblyActorRef))
  val registration                           = AkkaRegistration(connection, Prefix("nfiraos.ncc.trombone"), assemblyActorRef)
  val registrationResult: RegistrationResult = HttpLocationServiceFactory.makeLocalClient.register(registration).await

  def main(args: Array[String]): Unit = {}

}

class AssemblyApp extends Actor {
  override def receive: Receive = {
    case "Unregister" => AssemblyApp.registrationResult.unregister()
  }
}
