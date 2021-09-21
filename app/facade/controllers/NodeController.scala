package facade.controllers

import akka.actor.ActorSystem
import akka.stream.scaladsl.FileIO
import facade.repository._
import facade.{FacadeConfig, LogNames}
import play.api.libs.Files
import play.api.libs.json.JsString
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, MultipartFormData}
import play.api.{Configuration, Logger}

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
   * The main retrieval controller
   *
   * @param path    the path relating to the retrieval request
   * @param depth   the depth of the retrieval (meta-data) queries only. Defaults to 1.
   * @param meta    true if the meta is to be returned, false otherwise. Defaults to true.
   * @param content true if the content relating to the path should be retrieved, false otherwise.  Defaults to false.
   * @param version if retrieving content, the version to be retrieved.
   * @return
   */
  def get(path: String, depth: Int, content: Boolean, version: Option[Long]): Action[AnyContent] = {
    if (content) {
      log.trace("New content retrieval request accepted")
      retrieveContent(path, version)
    } else {
      log.trace("New meta-data retrieval request accepted")
      retrieveMetaData(path, depth)
    }
  }

  /**
   * Uploads new content to a given location. If the location represents a container (such as a folder) then a new document is added
   * using the payload.  If the location represents an existing document node, then a new version is added to the node
   * @param path the path of parent node
   * @return the updated [[play.api.libs.json.JsObject]] rendition of the node
   */
  def post(path : String) : Action[AnyContent] = Action.async { implicit request =>
    log.trace("New content upload request accepted")
    val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
    request.body.asMultipartFormData match {
      case Some(mp) =>
        if (mp.files.isEmpty){
          Future.successful(Ok(ResponseHelpers.failure(JsString("You must supply at least one file within a content upload request"))))
        }else{
          for(f <- mp.files){
            log.trace(s"Uploading content '${f.filename}'")
          }
        }
      case None =>
        Future.successful(Ok(ResponseHelpers.failure(JsString("To upload content, you must supply valid multi-part form content"))))
    }


    Future.successful(Ok(JsString("you attempted to upload a file")))
  }

  /**
   * Retrieves the content of a node (i.e. if it's a document then the document)
   * @param path the path to the node
   * @param version the version to retrieve, which defaults to the latest version
   * @return a valid [[play.api.libs.json.JsObject]] containing the rendition of the node
   */
  private def retrieveContent(path: String, version : Option[Long]) : Action[AnyContent] = Action.async {
    val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
    resolvedPathFuture flatMap {
      case Right(details) =>
        repository.retrieveNodeContent(details, version) map {
          case Right(info) =>
            Ok.sendFile(
              content = info.file,
              inline = false,
            ).withHeaders("Content-Type" -> info.contentType)
          case Left(t) =>
            Ok(ResponseHelpers.failure(JsString(t.getMessage)))
        }
      case Left(t) =>
        Future.successful(Ok(ResponseHelpers.failure(JsString(t.getMessage))))
    }
  }

  /**
   * Retrieves the meta-data associated with a node, as a [[play.api.libs.json.JsObject]]
   * @param path the path to the node
   * @param depth the depth of the recursive traversal required (i.e. node + children to depth)
   * @return a [[play.api.libs.json.JsObject]] representation of the node in question
   */
  private def retrieveMetaData(path : String, depth : Int) : Action[AnyContent] = Action.async{
    if (depth > facadeConfig.maximumTreeTraversalDepth) {
      Future.successful(Ok(ResponseHelpers.failure(JsString(s"Please don't try and exceed the maximum tree traversal depth of " +
        s"${facadeConfig.maximumTreeTraversalDepth}"))))
    }else {
      val resolvedPathFuture = repository.resolvePath(path.split('/').toList)
      resolvedPathFuture flatMap {
        case Right(details) => {
          repository.renderNodeToJson(details, depth) map {
            case Right(rendition) => {
              Ok(ResponseHelpers.success(rendition))
            }
            case Left(t) => {
              Ok(ResponseHelpers.failure(JsString(t.getMessage)))
            }
          }
        }
        case Left(t) => {
          Future.successful(Ok(ResponseHelpers.failure(JsString(t.getMessage))))
        }
      }
    }
  }

}
