package facade.cws

import com.opentext.cws.admin.{AdminService_Service, ServerInfo}
import com.opentext.cws.authentication._
import com.opentext.cws.content.ContentService_Service
import com.opentext.cws.docman.{OTAuthentication => _, _}
import facade.cws.DefaultCwsProxy.{EcmApiNamespace, OtAuthenticationHeaderName, wrapToken}
import facade.{FacadeConfig, LogNames}
import org.joda.time.format.DateTimeFormat
import play.api.cache.{NamedCache, SyncCacheApi}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString, JsValue}
import play.api.{Configuration, Logger}

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util
import java.util.GregorianCalendar
import javax.inject.{Inject, Singleton}
import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}
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
 *
 */
@Singleton
class DefaultCwsProxy @Inject()(configuration: Configuration,
                                lifecycle: ApplicationLifecycle,
                                @NamedCache("token-cache") tokenCache: SyncCacheApi,
                                @NamedCache("node-cache") nodeCache: SyncCacheApi,
                                authenticationService: Authentication_Service,
                                adminService: AdminService_Service,
                                documentManagementService: DocumentManagement_Service,
                                contentService: ContentService_Service,
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
   * Late bound CWS authentication client
   */
  private lazy val authClient = authenticationService.basicHttpBindingAuthentication

  /**
   * Late bound bound CWS admin client
   */
  private lazy val adminClient = adminService.basicHttpBindingAdminService(this)

  /**
   * Late bound bound CWS document management client
   */
  private lazy val docManClient = documentManagementService.basicHttpBindingDocumentManagement(this)

  /**
   * Late bound CWS content service client
   */
  private lazy val contentClient = contentService.basicHttpBindingContentService(this)

  /**
   * Used for metadata serialisation and update
   */
  private lazy val formatter = DateTimeFormat.forPattern("dd/MM/yyyy")


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
  private[DefaultCwsProxy] def resolveToken(): String = {
    val cachedToken = tokenCache.get("cachedToken")
    if (cachedToken.isDefined) {
      log.trace("Found cached authentication token")
      cachedToken.get.asInstanceOf[String]
    } else {
      try {
        val result = Await.result(authenticate(), 5 seconds)
        result match {
          case Right(s) =>
            val token = s.getAuthenticationToken
            log.trace("Caching authentication token")
            tokenCache.set("cachedToken", token, facadeConfig.tokenCacheLifetime)
            token
          case Left(ex) =>
            log.warn("Failed to obtain an authentication token")
            throw ex
        }
      } catch {
        case ex: Exception =>
          log.error(s"Failed to authenticate against OTCS: \"${ex.getMessage}\"")
          log.error("Unable to perform authentication against OTCS - check service status")
          throw new Throwable("OTCS authentication failed. Check service status & config")
        case ex: Throwable =>
          log.error(s"Failed to authenticate against OTCS: \"${ex.getMessage}\"")
          log.error("Unable to perform authentication against CWS - check service status")
          throw new Throwable("OTCS authentication failed. Check service status & config")
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
      } catch {
        case _: Throwable =>
          log.error("Unable to inject required outbound authentication token")
          log.error("Check previous errors relating to OTCS authentication")
          true
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
        case t =>
          log.error(t.getMessage)
          Left(t)
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
    blocking {
      nodeCache.get[Node](id.toHexString) match {
        case Some(node) =>
          log.trace(s"Node cache *hit* for id=$id")
          Future.successful(Right(node))
        case None =>
          log.trace(s"Node cache *miss* for id=$id")
          docManClient.getNode(id) map { node: Node =>
            if (node != null) {
              nodeCache.set(id.toHexString, node, facadeConfig.nodeCacheLifetime)
              Right(node)
            } else Left(new Throwable(s"Looks as if the node type for id=$id isn't supported by CWS"))
          } recover {
            case t =>
              log.error(t.getMessage)
              Left(t)
          }
      }
    }
  }

  /**
   * Attempts to retrieve the content associated with a given node version
   *
   * @param id            the id for the node
   * @param versionNumber the version to download.  If *None*, the latest version will be downloaded
   * @return A [[DownloadedContent]] instance containing the contents along with length and content type information
   */
  override def downloadNodeVersion(id: Long, versionNumber: Option[Long]): Future[CwsProxyResult[DownloadedContent]] = {
    log.trace(s"Content retrieval [$id, $versionNumber]")
    blocking {
      docManClient.getVersion(id, versionNumber.getOrElse(0)) flatMap { version =>
        docManClient.getVersionContentsContext(id, versionNumber.getOrElse(0)) flatMap { cookie =>
          contentClient.downloadContent(cookie) map { handler =>
            val tempFile = play.libs.Files.singletonTemporaryFileCreator().asScala().create(prefix = "fcs", suffix = s".${version
              .getFileType}").path.toFile
            Files.copy(handler.getInputStream, tempFile.toPath, StandardCopyOption.REPLACE_EXISTING)
            log.trace(s"Retrieved ${tempFile.length} bytes")
            Right(DownloadedContent(tempFile, tempFile.length, version.getMimeType))
          }
        }
      } recover {
        case t =>
          log.error(t.getMessage)
          Left(t)
      }
    }
  }

  /**
   * Uploads new content to a given parent node (either a folder or a document as a new version) and returns a new [[Node]]
   *
   * @param parentId the parent id of the node
   * @param meta     a [[JsObject]] containing the meta-data to be applied to the node
   * @param filename the filename to apply to the new content
   * @param source   a file containing the the content of the file to upload
   * @param size     the size of the content to upload
   * @return
   */
  override def uploadNodeContent(parentId: Long, meta: Option[JsObject], filename: String, source: Path, size: Long)
  : Future[CwsProxyResult[Node]] = {
    blocking {
      log.trace(s"Uploading ${size} bytes of content from '${source}'")
      nodeById(parentId) flatMap {
        case Right(parent) =>
          val attachment = createAttachment(source, filename)
          if (parent.isIsContainer) {
            log.trace(s"Uploading ${source} as new document object")
            docManClient.getNodeTemplate(parentId, "Document") flatMap { template =>
              updateMetadata(template.getMetadata, meta)
              template.setName(filename)
              docManClient.createNodeAndVersion(template, attachment) map { node =>
                Right(node)
              }
            }
          } else {
            log.trace(s"Uploading ${source} as new version object")
            updateMetadata(parent.getMetadata, meta)
            docManClient.addVersion(parentId, parent.getMetadata, attachment) map { version =>
              Right(parent)
            }
          }
        case Left(t) =>
          log.error(t.getMessage)
          Future.successful(Left(t))
      }
    }
  }

  /**
   * Updates an entire [[Metadata]] structure
   *
   * @param meta    the [[Metadata]] structure to be mutated/updated
   * @param updates a [[JsObject]] containing the update specification
   * @return Unit
   */
  @inline private[DefaultCwsProxy] def updateMetadata(meta: Metadata, updates: Option[JsObject]): Unit = {
    updates foreach { f =>
      for (update <- f.fields) {
        updateMetadataField(meta, update)
      }
    }
  }

  /**
   * Takes a source file and a required filename and then creates an attachment required for CWS upload calls
   *
   * @param source   the source [[Path]]
   * @param filename the required filename for the attachment
   * @return
   */
  private[DefaultCwsProxy] def createAttachment(source: Path, filename: String): Attachment = {
    val contents = Files.readAllBytes(source)
    val attachment = new Attachment()
    val ts = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar)
    attachment.setContents(contents)
    attachment.setFileName(filename)
    attachment.setFileSize(contents.length)
    attachment.setCreatedDate(ts)
    attachment.setModifiedDate(ts)
    attachment
  }

  /**
   * Given a two component path in the form *CategoryName*.*AttributeName* attempts to look up the associated [[DataValue]] within a CWS
   * [[Metadata]] structure
   *
   * @param path the path to the field
   * @param meta the [[Metadata]] instance to look in
   * @return an option. [[None]] if the field cannot be located within the supplied metadata structure
   */
  @inline private[DefaultCwsProxy] def lookupMetadataFieldByPath(path: String, meta: Metadata): Option[DataValue] = {
    val components = path.split('.')
    if (components.length == 2) {
      val groups = meta.getAttributeGroups.asScala
      for {
        ag <- groups.find(_.getDisplayName.equalsIgnoreCase(components(0)))
        dv <- ag.getValues.asScala.find(_.getDescription.equalsIgnoreCase(components(1)))
      } yield (dv)
    } else {
      None
    }
  }

  /**
   * Given a [[Metadata]] structure and a pair containing a field path and a [[JsValue]] will update the associated field
   *
   * @param meta   the [[Metadata]] instance to update
   * @param update the pair containing the path to the field and the value to update
   * @return a (possibly) updated instance of the [[Metadata]] structure
   */
  private def updateMetadataField(meta: Metadata, update: (String, JsValue)): Metadata = {
    val key = update._1
    val value = update._2

    lookupMetadataFieldByPath(key, meta) match {
      case Some(dv) => {
        dv match {
          case v: StringValue => {
            if (v.getValues.isEmpty) {
              v.getValues.add(value.asInstanceOf[JsString].value)
            } else {
              v.getValues.clear()
              v.getValues.add(value.asInstanceOf[JsString].value)
            }
          }
          case v: IntegerValue => {
            if (v.getValues.isEmpty) {
              v.getValues.add(value.asInstanceOf[JsNumber].value.toLong)
            } else {
              v.getValues.clear()
              v.getValues.add(value.asInstanceOf[JsString].value.toLong)
            }
          }
          case v: BooleanValue => {
            if (v.getValues.isEmpty) {
              v.getValues.add(value.asInstanceOf[JsBoolean].value)
            } else {
              v.getValues.clear()
              v.getValues.add(value.asInstanceOf[JsBoolean].value)
            }
          }
          case v: DateValue => {
            convertToXMLGregorianDate(value.asInstanceOf[JsString].value) match {
              case Some(d) => {
                if (v.getValues.isEmpty) {
                  v.getValues.add(d)
                } else {
                  v.getValues.clear()
                  v.getValues.add(d)
                }
              }
              case None => {}
            }
          }
        }
      }
      case None => {}
    }

    meta
  }

  /**
   * Does the horrible SOAP serialisation for date values
   *
   * @param value a string encoded date value
   * @return an option containing the [[XMLGregorianCalendar]] value for the passed in parameter
   */
  @inline private[DefaultCwsProxy] def convertToXMLGregorianDate(value: String): Option[XMLGregorianCalendar] = {
    try {
      val dt = formatter.parseDateTime(value)
      import java.util.GregorianCalendar
      import javax.xml.datatype.DatatypeFactory
      val calendar = new GregorianCalendar
      calendar.setTime(dt.toDate)
      val df = DatatypeFactory.newInstance
      Some(df.newXMLGregorianCalendar(calendar))
    }
    catch {
      case _: Throwable => None
    }
  }

  /**
   * Updates the metadata associated with a given node (based on its id). Note that a side-effect of calling this function is to re-cache
   * the target node so that subsequent reads of the node will be very quick
   *
   * @param id   the id of the node to update
   * @param meta the metadata to be applied to the node (updates only) in the form of KV pairs, where the key is of the form <CATEGORY>
   *             .<ATTRIBUTE>
   * @return the updated [[Node]]
   */
  override def updateNodeMetaData(id: Long, meta: JsObject): Future[CwsProxyResult[Node]] = {
    blocking {
      log.trace(s"Updating meta-data for node with id=$id")
      nodeById(id) flatMap {
        case Right(node) =>
          updateMetadata(node.getMetadata, Some(meta))
          nodeCache.set(id.toHexString, node, facadeConfig.nodeCacheLifetime)
          docManClient.updateNode(node) flatMap { _ =>
              nodeById(id)
          }
        case Left(t)=>
          log.error(t.getMessage)
          Future.successful(Left(t))
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
