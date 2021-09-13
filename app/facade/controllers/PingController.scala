package facade.controllers

import akka.actor.ActorSystem
import facade.cws.CwsProxy
import facade.db.DbContext
import facade.repository.RepositoryState
import facade.{FacadeConfig, LogNames}
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PingController @Inject()(val cc: ControllerComponents,
                               val system: ActorSystem,
                               val configuration: Configuration,
                               val dbContext: DbContext,
                               val cwsProxy : CwsProxy,
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

    val result = for {
      t <- cwsProxy.authenticate()
      v <- dbContext.schemaVersion()
    } yield (v)

    result.map {
      case Right(version) => {
        val state = RepositoryState(facadeVersion = facadeConfig.version,
          systemIdentifier = facadeConfig.systemIdentifier,
          schemaVersion = version)
        Ok(ResponseHelpers.success(Json.toJson(state)))
      }
      case Left(t) => Ok(ResponseHelpers.failure(JsString(t.getMessage)))
    }
  }

}
