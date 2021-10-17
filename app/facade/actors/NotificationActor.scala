
package facade.actors

import akka.actor.{Actor, Props}
import com.opentext.cws.docman.{DataValue, Metadata, Node, StringValue}
import facade.{FacadeConfig, LogNames}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.JodaWrites._
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.WSClient
import play.utils.UriEncoding

import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Companion object for the notification actor
 */
object NotificationActor {

  object WSP {

    /**
     *
     * @param ws     web service client
     * @param path   Path information relating to a specific operation
     * @param node   The CWS node which is used to derive the notification message properties
     * @param config The current application configuration, including notification endpoints etc...
     */
    case class NotifyWSP(ws: WSClient, path: String, node: Node, config: FacadeConfig)

    /**
     *
     * @param attributePath
     * @param attributeValue
     * @param templateId
     * @param templateName
     */
    case class WSPDocumentTypeSettings(attributePath: String, attributeValue: String, templateId: String, templateName: String)

  }

  /**
   * Props for the [[NotificationActor]]
   *
   * @return
   */
  def props(ws: WSClient) = Props(new NotificationActor(ws: WSClient))

}


/**
 * Actor which is pooled and used to generate notification events after specific operations are carried out, i.e. document uploads
 */
class NotificationActor(ws: WSClient) extends Actor {

  import NotificationActor.WSP._

  /**
   * Logger for this class
   */
  lazy val log: Logger = Logger(LogNames.ControllerLogger)

  /**
   * Wait for an instance of [[NotifyWSP]] and then send out a notification
   *
   * @return
   */
  override def receive: Receive = {
    case NotifyWSP(ws, path, node, config) =>
      log.trace(s"Received a new WSP notification request: $path")
      val bpidAndPath = extractBPIDAndPath(path)
      val payload = basePayload(bpidAndPath._1) ++ nodePayload(node, path,
        "Document Information.Document Class", "Document Information.Document Type")
      sendNotificationPayload(ws, config, payload)
  }


  /**
   * Does the actual notification - just fires off a HTTP request to the configured endpoint
   *
   * @param ws      an instance of [[WSClient]]
   * @param config  the current [[FacadeConfig]]
   * @param payload the payload to send
   * @return
   */
  protected def sendNotificationPayload(ws: WSClient, config: FacadeConfig, payload: JsObject) = {
    log.trace(s"Sending notification payload to endpoint: ${config.notificationEndpoint}")
    log.trace(s"Current notification payload is: $payload")
    ws.url(config.notificationEndpoint)
      .post(payload)
  }


  /**
   * Extracts a business partner id from a given path
   *
   * @param path the path to extract the business partner id from
   * @return
   */
  protected def extractBPIDAndPath(path: String) = {
    val elements = UriEncoding.decodePath(path, "UTF-8").split("/").toList
    (elements.tail.head, elements.tail.tail.mkString("/"))
  }


  /**
   * Constructs a [[JsObject]] containing the basic boilerplate fields required by the WSP channel. The only field completed by the
   * facade is the business partner ID
   *
   * @param bpid the business partner id
   * @return
   */
  protected def basePayload(bpid: String): JsObject = Json.obj(
    "TemplateId" -> "",
    "TemplateName" -> "",
    "BusinessPartnerID" -> bpid,
    "AccountNumber" -> "",
    "StatementNumber" -> "",
    "StatementDate" -> ""
  )

  /**
   * Builds the payload to be sent via the WSP notification channel
   *
   * @param node               the originating [[Node]] instance
   * @param path               the path for the node
   * @param classAttributePath the metadata path for the document class (this is specific to Discovery implementation)
   * @param typeAttributePath  the metadata path for the document type (this is specific to the Discovery implementation)
   * @return a new [[JsObject]] containing the information relating to a new node/uploaded document
   */
  protected def nodePayload(node: Node, path: String, classAttributePath: String, typeAttributePath: String): JsObject = {
    val elements = UriEncoding.decodePath(path, "UTF-8").split("/").toList
    Json.obj(
      "CreationDateTime" -> new DateTime(node.getCreateDate.toGregorianCalendar.getTime),
      "DocumentType" -> getSingleStringDataValue(typeAttributePath, node.getMetadata).getOrElse[String]("Undefined"),
      "DocumentClass" -> getSingleStringDataValue(classAttributePath, node.getMetadata).getOrElse[String]("Undefined"),
      "FilePath" -> elements.tail.mkString("/"),
      "FolderLocation" -> elements.reverse.head,
      "QualifiedFilename" -> (elements.tail.mkString("/") + "/" + node.getName())
    )
  }

  /**
   * Given a valid [[Metadata]] instance and a [[String]] path to a given attribute in the form categoryName.attributeName, returns an
   * [[Option]] which will either contain the relevant [[DataValue]] or None.
   *
   * @param path
   * @param metadata
   */
  def locateDataValue(path: String, metadata: Metadata): Option[DataValue] = {
    val components = path.split('.')
    if (components.length == 2) {
      val groups = metadata.getAttributeGroups.asScala
      for {
        ag <- groups.find(_.getDisplayName.equalsIgnoreCase(components(0)))
        dv <- ag.getValues.asScala.find(_.getDescription.equalsIgnoreCase(components(1)))
      } yield dv
    } else {
      None
    }
  }

  /**
   * Extracts a single string value from a supplied [[Metadata]] instance, given a path
   *
   * @param path     the path to the string value location in the format "category"."attribute"
   * @param metadata the [[Metadata]] instance
   * @return an [[Option[String]]] containing the value, or not
   */
  def getSingleStringDataValue(path: String, metadata: Metadata): Option[String] = {
    locateDataValue(path, metadata) match {
      case Some(dv) => {
        dv match {
          case v: StringValue => {
            if (v.getValues.isEmpty) {
              None
            } else {
              v.getValues.asScala.toList.headOption
            }
          }
        }
      }
      case None => None
    }
  }

  /**
   * Just log actor startup
   */
  override def preStart(): Unit = {
    log.trace("Starting notification actor")
    super.preStart()
  }

  /**
   * Just log actor shutdown
   */
  override def postStop(): Unit = {
    log.trace("Stopping notification actor")
    super.postStop()
  }
}

