package facade.controllers

import akka.actor.ActorSystem
import akka.stream.scaladsl.FileIO
import facade.repository._
import facade.{FacadeConfig, LogNames}
import play.api.libs.json.JsString
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
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
  lazy val log: Logger = Logger(LogNames.MainLogger)

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
      retrieveContent(path, version)
    } else {
      retrieveMetaData(path, depth)
    }
  }

  /**
   *
   * @param path
   * @param version
   * @return
   */
  private def retrieveContent(path: String, version : Option[Long]) : Action[AnyContent] = Action.async {
    val resolution = repository.resolvePath(path.split('/').toList)
    resolution flatMap {
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
   *
   * @param path
   * @param depth
   * @return
   */
  private def retrieveMetaData(path : String, depth : Int) : Action[AnyContent] = Action.async{
    if (depth > facadeConfig.maximumTreeTraversalDepth) {
      Future.successful(Ok(ResponseHelpers.failure(JsString(s"Please don't try and exceed the maximum tree traversal depth of " +
        s"${facadeConfig.maximumTreeTraversalDepth}"))))
    }else {
      val resolution = repository.resolvePath(path.split('/').toList)
      resolution flatMap {
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
