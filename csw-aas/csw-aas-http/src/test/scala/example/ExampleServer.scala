package example

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.GenericUnmarshallers
import csw.aas.core.deployment.AuthConfig
import csw.aas.core.token.TokenFactory
import csw.aas.http.AuthorizationPolicy.{EmptyPolicy, ResourceRolePolicy}
import csw.aas.http.{Authentication, SecurityDirectives}
import csw.logging.scaladsl.LoggingSystemFactory
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

object ExampleServer extends HttpApp with App with GenericUnmarshallers with PlayJsonSupport {

  LoggingSystemFactory.start("example-server", "", "", ActorSystem())

  private val authConfig     = AuthConfig.loadFromAppConfig
  private val tokenFactory   = new TokenFactory(authConfig)
  private val authentication = new Authentication(tokenFactory)
  private val directives     = SecurityDirectives(authentication, authConfig)

  import directives._
  private val HOST = "localhost"
  private val PORT = 9003

  override protected def routes: Route = pathPrefix("config") {
    head {
      complete("HEAD OK")
    } ~
    sPost(ResourceRolePolicy("admin")) { _ =>
      complete("POST OK")
    } ~
    post {
      complete("UNAUTHORIZED POST")
    } ~
    pathPrefix(Segment ~ PathEnd) { subsystem =>
      sGet(ResourceRolePolicy(s"${subsystem}_admin")) { _ =>
        //your route code goes here
        complete(s"SUCCESS. you have access to subsystem $subsystem via ${subsystem}_admin role")
      }
    } ~ sPut(EmptyPolicy) { token =>
      complete(s"Authenticated ${token.preferred_username}")
    }
  }

  startServer(HOST, PORT)
}
