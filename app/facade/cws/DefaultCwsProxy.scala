package facade.cws

import com.opentext.cws.authentication._
import facade.cws.DefaultCwsProxy.wrapToken
import facade.{FacadeConfig, LogNames}
import play.api.cache.AsyncCacheApi
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import java.util
import javax.inject.{Inject, Singleton}
import javax.xml.namespace.QName
import javax.xml.ws.handler.MessageContext
import javax.xml.ws.handler.soap.{SOAPHandler, SOAPMessageContext}
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
                                implicit val cwsProxyExecutionContext: CwsProxyExecutionContext) extends CwsProxy with SOAPHandler[SOAPMessageContext] {

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



  /**
   * Attempts an authentication and returns the resultant [[OTAuthentication]] structure containing the token
   *
   * @return an [[OTAuthentication]] structure containing the authentication token, otherwise an exception
   */
  def authenticate(): Future[CwsProxyResult[OTAuthentication]] = {
    val client = authenticationService.basicHttpBindingAuthentication
    //val client = authenticationService.basicHttpBindingAuthentication(this)
    val user = facadeConfig.cwsUser.getOrElse("Admin")
    val password = facadeConfig.cwsPassword.getOrElse("livelink")
    client.authenticateUser(user, password)
      .map(s => Right(wrapToken(s)))
      .recover({case t => log.error(t.getMessage); throw t})
  }

  /**
   * Implementation of [[SOAPHandler]]
   * @return
   */
  override def getHeaders: util.Set[QName] = null

  /**
   * Implementation of [[SOAPHandler]]
   * @param context the inbound/outbound [[SOAPMessageContext]]
   * @return will always return true in order make sure that the message is processed by downstream components
   */
  override def handleMessage(context: SOAPMessageContext): Boolean = {
    val outbound = context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY).asInstanceOf[java.lang.Boolean]
    val message = context.getMessage
    if (outbound){
      log.trace("Processing outbound message")
    }else{
      log.trace("Processing inbound message")
    }
    true
  }

  /**
   * Implementation of [[SOAPHandler]]
   * @param context the inbound/outbound [[SOAPMessageContext]]
   * @return just logs and then returns true in order to allow for further processing
   */
  override def handleFault(context: SOAPMessageContext): Boolean = {
    log.warn(s"SOAP fault received, message is ${context.getMessage}")
    true
  }

  override def close(context: MessageContext): Unit = ()
}

/**
 * Companion object for static methods etc...
 */
object DefaultCwsProxy {

  /**
   * The namespace to use for authentication headers
   */
  lazy val EcmApiNamespace = "urn:api.ecm.opentext.com"

  /**
   * Helper function that just takes a token and wraps it as an instance of [[OTAuthentication]]
   * @param token the token to be wrapped
   * @return a new instance of [[OTAuthentication]]
   */
  private def wrapToken(token : String) : OTAuthentication = {
    val auth = new OTAuthentication()
    auth.setAuthenticationToken(token)
    auth
  }
}
