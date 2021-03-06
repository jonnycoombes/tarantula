package facade.controllers

import akka.actor.ActorSystem
import akka.stream.{IOResult, scaladsl}
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import facade.repository._
import facade.{FacadeConfig, LogNames}
import play.api.http.HttpEntity
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, MultipartFormData, ResponseHeader, Result}
import play.api.{Configuration, Logger}
import play.api.libs.Files

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NodeController @Inject()(val cc: ControllerComponents,
                               val system: ActorSystem,
                               val configuration: Configuration,
                               val repository: Repository,
                               implicit val ec: ExecutionContext) extends AbstractController(cc) {

  /**
   * The logger for this controller
   */
  lazy val log: Logger = Logger(LogNames.ControllerLogger)

  /**
   * The current system configuration
   */
  private val facadeConfig = FacadeConfig(configuration)

  /**
   * Attempts to parse a boolean value out from a given [[Option[String]]]
   *
   * @param value the value to parse
   * @return either a true or a false
   */
  @inline private def tryParseBoolean(value: Option[String]): Boolean = {
    value match {
      case None => false
      case Some(str) =>
        str.toLowerCase match {
          case "true" => true
          case _ => false
        }
    }
  }

  /**
   * The main retrieval controller
   *
   * @param path    the path relating to the retrieval request
   * @param depth   the depth of the retrieval (meta-data) queries only. Defaults to 1.
   * @param content true if the content relating to the path should be retrieved, false otherwise.  Defaults to false.
   * @param version if retrieving content, the version to be retrieved.
   * @return
   */
  def get(path: String, depth: Option[Int], content: Option[String], version: Option[Long]): Action[AnyContent] =
    if (tryParseBoolean(content)) {
      log.trace("New content retrieval request accepted")
      retrieveContent(path, version)
    } else {
      log.trace("New meta-data retrieval request accepted")
      retrieveMetaData(path, depth.getOrElse(facadeConfig.defaultTreeTraversalDepth))
    }

  /**
   * Performs a search against the underlying repository, utilising the optimised SQL-based search interpreter
   *
   * @param query a query conforming to the grammar defined within the [[facade.db.Search.QueryParser]] module
   * @return a [[List[JsObject]] instance containing the results of the search (if any)
   */
  def search(query: String): Action[AnyContent] = Action.async {
    Future.successful(Ok(ResponseHelpers.success(JsString("You requested a search"))))
    repository.search(query) map {
      case Right(results) =>
        Ok(ResponseHelpers.success(results.foldRight(JsArray())((o, arr) => arr.append(o))))
      case Left(t) => Ok(ResponseHelpers.throwableFailure(t))
    }
  }

  private[NodeController] def parseMetadata(multiPart: MultipartFormData[Files.TemporaryFile]): Option[JsObject] = {
    var meta: Option[JsObject] = None
    multiPart.dataParts.get("meta") match {
      case Some(s) =>
        meta = Some(Json.parse(s.head).asInstanceOf[JsObject])
      case None =>
        meta = None
    }
    meta
  }

  /**
   * Uploads new content to a given location. If the location represents a container (such as a folder) then a new document is added
   * using the payload.  If the location represents an existing document node, then a new version is added to the node
   *
   * @param path the path of parent node
   * @return the updated [[play.api.libs.json.JsObject]] rendition of the node
   */
  def post(path: String): Action[AnyContent] = Action.async { implicit request =>
    log.trace("New content upload request accepted")
    request.body.asMultipartFormData match {
      case Some(mp) =>
        if (mp.files.isEmpty) {
          Future.successful(Ok(ResponseHelpers.failure(JsString("You must supply at least one file within a content upload request"))))
        } else {
          val meta = parseMetadata(mp)
          val file = mp.files.head
          log.trace(s"Uploading content '${file.filename}'")
          val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
          resolvedPathFuture flatMap {
            case Right(details) =>
              repository.uploadContent(details, meta,path, file.filename, file.ref.path, file.fileSize) map {
                case Right(rendition) =>
                  Ok(ResponseHelpers.success(rendition))
                case Left(t) =>
                  Ok(ResponseHelpers.failure(JsString(t.getMessage)))
              }
            case Left(t) =>
              Future.successful(Ok(ResponseHelpers.failure(JsString(t.getMessage))))
          }
        }
      case None =>
        Future.successful(Ok(ResponseHelpers.failure(JsString("To upload content, you must supply valid multi-part form content"))))
    }
  }

  /**
   * Parses a meta-data update from the inbound request body, (as a [[JsObject]] instance) and then uses this to update the meta-data for
   * a given node
   *
   * @param path the path to the node to update
   * @return
   */
  def patch(path: String): Action[AnyContent] = Action.async { implicit request =>

    request.body.asJson match {
      case Some(json) =>
        val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
        resolvedPathFuture flatMap {
          case Right(details) =>
            repository.updateNodeMetaData(details, json.asInstanceOf[JsObject]) map {
              case Right(rendition) =>
                Ok(ResponseHelpers.success(rendition))
              case Left(t) =>
                Ok(ResponseHelpers.failure(JsString(t.getMessage)))
            }
          case Left(t) =>
            Future.successful(Ok(ResponseHelpers.failure(JsString(t.getMessage))))
        }

      case None => Future.successful(Ok(ResponseHelpers.failure(JsString("No JSON payload presented as part of a PATCH request"))))
    }

  }

  /**
   * Needed because sometime Content Server will return null content types for files which it doesn't recognise. The most obvious of
   * these is application/json or anything with a non-standard file extension
   * @param contentType the content type returned by OTCS
   * @return either an actual content type, or alternatively the default content type of "text/plain"
   */
  @inline private def fallbackContentType(contentType : String) : String = {
    if (contentType == null){
      "text/plain"
    }else{
      contentType
    }
  }

  /**
   * Retrieves the content of a node (i.e. if it's a document then the document)
   *
   * @param path    the path to the node
   * @param version the version to retrieve, which defaults to the latest version
   * @return a valid [[play.api.libs.json.JsObject]] containing the rendition of the node
   */
  private def retrieveContent(path: String, version: Option[Long]): Action[AnyContent] = Action.async {
    val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
    resolvedPathFuture flatMap {
      case Right(details) =>
        repository.retrieveNodeContent(details, version) map {
          case Right(info) =>
            val source: scaladsl.Source[ByteString, Future[IOResult]] = FileIO.fromPath(info.file.toPath)
            Result(
              header = ResponseHeader(200, Map.empty),
              body = HttpEntity.Streamed(source, Some(info.length), Some(fallbackContentType(info.contentType)))
            )
          case Left(t) =>
            Ok(ResponseHelpers.failure(JsString(t.getMessage)))
        }
      case Left(t) =>
        Future.successful(Ok(ResponseHelpers.failure(JsString(t.getMessage))))
    }
  }

  /**
   * Retrieves the meta-data associated with a node, as a [[play.api.libs.json.JsObject]]
   *
   * @param path  the path to the node
   * @param depth the depth of the recursive traversal required (i.e. node + children to depth)
   * @return a [[play.api.libs.json.JsObject]] representation of the node in question
   */
  private def retrieveMetaData(path: String, depth: Int): Action[AnyContent] = Action.async {
    if (depth > facadeConfig.maximumTreeTraversalDepth) {
      Future.successful(Ok(ResponseHelpers.failure(JsString(s"Please don't try and exceed the maximum tree traversal depth of " +
        s"${facadeConfig.maximumTreeTraversalDepth}"))))
    } else {
      val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
      resolvedPathFuture flatMap {
        case Right(details) =>
          repository.renderNodeToJson(details, depth) map {
            case Right(rendition) =>
              Ok(ResponseHelpers.success(rendition))
            case Left(t) =>
              Ok(ResponseHelpers.failure(JsString(t.getMessage)))
          }
        case Left(t) =>
          Future.successful(Ok(ResponseHelpers.failure(JsString(t.getMessage))))
      }
    }
  }

}
