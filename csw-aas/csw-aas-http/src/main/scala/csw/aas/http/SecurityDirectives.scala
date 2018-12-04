package csw.aas.http

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directives.{authenticateOAuth2, authorize => keycloakAuthorize, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.AuthenticationDirective
import csw.aas.core.deployment.AuthConfig
import csw.aas.core.token.AccessToken
import csw.aas.http.AuthorizationPolicy.{EmptyPolicy, _}
import org.keycloak.adapters.KeycloakDeployment

class SecurityDirectives(authentication: Authentication, authConfig: AuthConfig) {

  private val keycloakDeployment: KeycloakDeployment = authConfig.getDeployment

  private val realm: String        = keycloakDeployment.getRealm
  private val resourceName: String = keycloakDeployment.getResourceName

  private[aas] def authenticate: AuthenticationDirective[AccessToken] = authenticateOAuth2(realm, authentication.authenticator)

  private[aas] def authorize(authorizationPolicy: AuthorizationPolicy, accessToken: AccessToken): Directive0 =
    authorizationPolicy match {
      case ResourceRolePolicy(name)         => keycloakAuthorize(accessToken.hasResourceRole(name, resourceName))
      case RealmRolePolicy(name)            => keycloakAuthorize(accessToken.hasRealmRole(name))
      case PermissionPolicy(name, resource) => keycloakAuthorize(accessToken.hasPermission(name, resource))
      case CustomPolicy(predicate)          => keycloakAuthorize(predicate(accessToken))
      case EmptyPolicy                      => Directive.Empty
    }

  private def sMethod(httpMethod: HttpMethod, authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] =
    method(httpMethod) & authenticate.flatMap(token => authorize(authorizationPolicy, token) & provide(token))

  def sPost(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] = sMethod(HttpMethods.POST, authorizationPolicy)

  def sGet(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] = sMethod(HttpMethods.GET, authorizationPolicy)

  def sPut(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] = sMethod(HttpMethods.PUT, authorizationPolicy)

  def sDelete(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] =
    sMethod(HttpMethods.DELETE, authorizationPolicy)

  def sPatch(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] = sMethod(HttpMethods.PATCH, authorizationPolicy)

  def sHead(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] = sMethod(HttpMethods.HEAD, authorizationPolicy)

  def sConnect(authorizationPolicy: AuthorizationPolicy): Directive1[AccessToken] =
    sMethod(HttpMethods.CONNECT, authorizationPolicy)
}

//todo: do we really need this object and factory, why not simply new?
object SecurityDirectives {
  def apply(authentication: Authentication, authConfig: AuthConfig): SecurityDirectives =
    new SecurityDirectives(authentication, authConfig)
}
