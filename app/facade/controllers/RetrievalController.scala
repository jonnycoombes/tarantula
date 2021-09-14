package facade.controllers

import akka.actor.ActorSystem
import facade.{FacadeConfig, LogNames}
import facade.cws.CwsProxy
import facade.db.DbContext
import facade.repository._
import play.api.libs.json.{JsNumber, JsString}
import play.api.{Configuration, Logger}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RetrievalController @Inject()(val cc: ControllerComponents,
                                    val system: ActorSystem,
                                    val configuration: Configuration,
                                    val repository : Repository,
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
   * @param path the path relating to the retrieval request
   * @param depth the depth of the retrieval (meta-data) queries only. Defaults to 1.
   * @param meta true if the meta is to be returned, false otherwise. Defaults to true.
   * @param content true if the content relating to the path should be retrieved, false otherwise.  Defaults to false.
   * @param version if retrieving content, the version to be retrieved.
   * @return
   */
  def get(path: String, depth : Int, meta : Boolean, content : Boolean, version : Int): Action[AnyContent] = Action.async {
    val resolutionFuture = repository.resolvePath(path)
    resolutionFuture map {
      case Right(id) => {
        Ok(ResponseHelpers.success(JsNumber(id)))
      }
      case Left(t) => {
        Ok(ResponseHelpers.failure(JsString(t.getMessage)))
      }
    }
  }

}
