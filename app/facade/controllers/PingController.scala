package facade.controllers

import akka.actor.ActorSystem
import facade.db.DbContext
import facade.repository.RepositoryState
import facade.{FacadeConfig, LogNames}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PingController @Inject()(val cc : ControllerComponents,
                               val system : ActorSystem,
                               val configuration : Configuration,
                               val dbContext : DbContext,
                               implicit val ec: ExecutionContext) extends AbstractController(cc){

  /**
   * The logger for this controller
   */
  lazy val log: Logger = Logger(LogNames.MainLogger)

  private val facadeConfig = FacadeConfig(configuration)

  /**
   * Generates a ping response, which may be used to check the health of the facade service
   * @return
   */
  def ping() : Action[AnyContent] = Action.async {
    val result = for {
        v <- dbContext.SchemaVersion()
    } yield (v)
    result.map(v => {
        val state = RepositoryState(facadeVersion = facadeConfig.version,
          systemIdentifier = facadeConfig.systemIdentifier,
          schemaVersion = v)
      Ok(ResponseHelpers.success(Json.toJson(state)))
    })
  }

}
