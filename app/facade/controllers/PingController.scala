package facade.controllers

import akka.actor.ActorSystem
import facade.cws.CwsProxy
import facade.db.DbContext
import facade.repository.Repository
import facade.{FacadeConfig, LogNames}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PingController @Inject()(val cc: ControllerComponents,
                               val system: ActorSystem,
                               val configuration: Configuration,
                               val repository : Repository,
                               implicit val ec: ExecutionContext) extends AbstractController(cc) {

  /**
   * The logger for this controller
   */
  lazy val log: Logger = Logger(LogNames.MainLogger)

  private val facadeConfig = FacadeConfig(configuration)

  /**
   * Generates a ping response, which may be used to check the health of the facade service
   *
   * @return
   */
  def ping(): Action[AnyContent] = Action.async {
    val future = repository.repositoryState()
    future map { s =>
      Ok(ResponseHelpers.success(Json.toJson(s)))
    } recover  { t =>
      Ok(ResponseHelpers.throwableFailure(t))
    }
  }
}
