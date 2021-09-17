package facade.cws

import com.opentext.cws.admin.{AdminService_Service, ServerInfo}
import com.opentext.cws.authentication._
import com.opentext.cws.docman.{DocumentManagement_Service, Node}
import facade.cws.DefaultCwsProxy.{EcmApiNamespace, OtAuthenticationHeaderName, wrapToken}
import facade.{FacadeConfig, LogNames}
import play.api.cache.{AsyncCacheApi, NamedCache, SyncCacheApi}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import java.util
import javax.inject.{Inject, Singleton}
import javax.xml.namespace.QName
import javax.xml.ws.handler.MessageContext
import javax.xml.ws.handler.soap.{SOAPHandler, SOAPMessageContext}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, blocking}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

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
                                @NamedCache("token-cache") tokenCache: SyncCacheApi,
                                @NamedCache("node-cache") nodeCache : SyncCacheApi,
                                authenticationService: Authentication_Service,
                                adminService: AdminService_Service,
                                documentManagementService: DocumentManagement_Service,
                                implicit val cwsProxyExecutionContext: CwsProxyExecutionContext) extends CwsProxy with
  SOAPHandler[SOAPMessageContext] {

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

  /**
   * Pre-bound CWS authentication client
   */
  private lazy val authClient = authenticationService.basicHttpBindingAuthentication

  /**
   * Pre-bound CWS admin client
   */
  private lazy val adminClient = adminService.basicHttpBindingAdminService(this)

  /**
   * Pre-bound CWS document management client
   */
  private lazy val docManClient = documentManagementService.basicHttpBindingDocumentManagement(this)

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
    blocking {
      val user = facadeConfig.cwsUser.getOrElse("Admin")
      val password = facadeConfig.cwsPassword.getOrElse("livelink")
      authClient.authenticateUser(user, password)
        .map(s => Right(wrapToken(s)))
        .recover({ case t => log.error(t.getMessage); throw t })
    }
  }


  /**
   * Looks in the cache for a current token, or if not present, will authenticate in order to get a new token
   *
   * @return a valid authentication token
   */
  private def resolveToken(): String = {
    val cachedToken = tokenCache.get("cachedToken")
    if (cachedToken.isDefined) {
      log.trace("Found cached authentication token")
      cachedToken.get.asInstanceOf[String]
    } else {
      try {
        val result = Await.result(authenticate(), 5 seconds)
        result match {
          case Right(s) => {
            val token = s.getAuthenticationToken
            log.trace("Caching authentication token")
            tokenCache.set("cachedToken", token, facadeConfig.tokenCacheLifetime)
            token
          }
          case Left(ex) => {
            log.warn("Failed to obtain an authentication token")
            throw ex
          }
        }
      } catch {
        case ex : Exception => {
          log.error(s"Failed to authenticate against OTCS: \"${ex.getMessage}\"")
          log.error("Unable to perform authentication against OTCS - check service status")
          throw new Throwable("OTCS authentication failed. Check service status & config")
        }
        case ex : Throwable => {
          log.error(s"Failed to authenticate against OTCS: \"${ex.getMessage}\"")
          log.error("Unable to perform authentication against CWS - check service status")
          throw new Throwable("OTCS authentication failed. Check service status & config")
        }
      }
    }
  }

  /**
   * Implementation of [[SOAPHandler]]
   *
   * @return
   */
  override def getHeaders: util.Set[QName] = null

  /**
   * Implementation of [[SOAPHandler]]
   *
   * @param context the inbound/outbound [[SOAPMessageContext]]
   * @return will always return true in order make sure that the message is processed by downstream components
   */
  override def handleMessage(context: SOAPMessageContext): Boolean = {
    val outbound = context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY).asInstanceOf[java.lang.Boolean]
    val message = context.getMessage
    if (outbound) {
      try {
        val header = message.getSOAPPart.getEnvelope.addHeader()
        val authElement = header.addHeaderElement(new QName(EcmApiNamespace, "OTAuthentication"))
        val tokenElement = authElement.addChildElement(new QName(EcmApiNamespace, "AuthenticationToken"))
        tokenElement.addTextNode(resolveToken())
        true
      }catch{
        case t : Throwable => {
          log.error("Unable to inject required outbound authentication token")
          log.error("Check previous errors relating to OTCS authentication")
          true
        }
      }
    } else {
      val headerElements = message.getSOAPPart.getEnvelope.getHeader.examineAllHeaderElements
      for (element <- headerElements.asScala) {
        if (element.getElementName.getLocalName == OtAuthenticationHeaderName) {
          //Await.result(cache.set("cachedToken", element.getFirstChild.getFirstChild.getNodeValue, 15 seconds), 5 seconds)
        }
      }
      true
    }
  }

  /**
   * Implementation of [[SOAPHandler]]
   *
   * @param context the inbound/outbound [[SOAPMessageContext]]
   * @return just logs and then returns true in order to allow for further processing
   */
  override def handleFault(context: SOAPMessageContext): Boolean = {
    log.warn(s"SOAP fault received, message is ${context.getMessage}")
    true
  }

  /**
   * Implementation of [[SOAPHandler]]
   *
   * @param context the inbound/outbound [[SOAPMessageContext]]
   */
  override def close(context: MessageContext): Unit = ()

  /**
   * Async wrapped call to [[com.opentext.cws.admin.AdminService]] GetServerInfo
   *
   * @return
   */
  override def serverInfo(): Future[CwsProxyResult[ServerInfo]] = {
    blocking {
      adminClient.getServerInfo map { info: ServerInfo =>
        Right(info)
      } recover {
        case t => {
          log.error(t.getMessage)
          Left(t)
        }
      }
    }
  }

  /**
   * Retrieve a node based on it's id
   *
   * @param id the id of the node
   * @return a [[Future]] wrapping a [[CwsProxyResult]]
   */
  override def nodeById(id: Long): Future[CwsProxyResult[Node]] = {
    blocking{
      nodeCache.get[Node](id.toHexString) match {
        case Some(node) => {
          log.trace(s"Node cache *hit* for id=${id}")
          Future.successful(Right(node))
        }
        case None => {
          log.trace(s"Node cache *miss* for id=${id}")
          docManClient.getNode(id) map { node : Node =>
            if (node != null) {
              nodeCache.set(id.toHexString, node, facadeConfig.nodeCacheLifetime)
              Right(node)
            }else{
              Left(new Throwable(s"Looks as if the node type for id=${id} isn't supported by CWS"))
            }
          } recover {
            case t => {
              log.error(t.getMessage)
              Left(t)
            }
          }
        }
      }
    }
  }
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
   * The standard name for the OT authentication SOAP header used in CWS calls
   */
  lazy val OtAuthenticationHeaderName = "OTAuthentication"

  /**
   * The standard name for the authentication token passed to CWS
   */
  lazy val OtAuthenticationTokenName = "AuthenticationToken"

  /**
   * Helper function that just takes a token and wraps it as an instance of [[OTAuthentication]]
   *
   * @param token the token to be wrapped
   * @return a new instance of [[OTAuthentication]]
   */
  private def wrapToken(token: String): OTAuthentication = {
    val auth = new OTAuthentication()
    auth.setAuthenticationToken(token)
    auth
  }
}
