package facade.cws

import com.opentext.{Authentication, Authentication_Service, OTAuthentication}
import facade.{FacadeConfig, LogNames}
import play.api.cache.AsyncCacheApi
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Default implementation of the [[CwsProxy]] trait. Implements transparent caching of authentication material, and all necessary
 * plumbing to make CWS out-calls as painless as possible.
 *
 * @param configuration current [[Configuration]] instance
 * @param lifecycle     in order to add any lifecycle hooks
 * @param cache         in case a cache is required
 */
@Singleton
class DefaultCwsProxy @Inject()(configuration: Configuration,
                                lifecycle: ApplicationLifecycle,
                                cache: AsyncCacheApi,
                                authenticationService: Authentication_Service,
                                implicit val cwsProxyExecutionContext: CwsProxyExecutionContext) extends CwsProxy {

  /**
   * The log used by the repository
   */
  private val log = Logger(LogNames.CwsProxyLogger)
  /**
   * The current facade configuration
   */
  private val facadeConfig = FacadeConfig(configuration)

  if (facadeConfig.cwsUser.isEmpty) log.warn("No CWS user name has been supplied, please check the system configuration")
  if (facadeConfig.cwsPassword.isEmpty) log.warn("No CWS password has been supplied, please check the system configuration")

  lifecycle.addStopHook { () =>
    Future.successful({
      log.debug("CwsProxy stop hook called")
    })
  }

  def authenticate(): Future[CwsProxyResult[OTAuthentication]] = {
    val client = authenticationService.basicHttpBindingAuthentication
    val user = facadeConfig.cwsUser.getOrElse("Admin")
    val password = facadeConfig.cwsPassword.getOrElse("livelink")
    client.authenticateUser(user, password).map(s => {
      val auth = new OTAuthentication()
      auth.setAuthenticationToken(s)
      Right(auth)
    })
  }

}
