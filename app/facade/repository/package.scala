package facade

import akka.actor.ActorSystem
import facade.db.DbContext
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json.{JsValue, Json, Writes}

import javax.inject.{Inject, Singleton}

/**
 * Top level package object for the repository package.  Statics, helpers etc...go here
 */
package object repository {

  /**
   * A [[CustomExecutionContext]] for use by [[Repository]] implementations, so that operations are performed within their own thread
   * pool
   * @param system an injected [[ActorSystem]]
   */
  @Singleton
  class RepositoryExecutionContext @Inject()(system : ActorSystem) extends CustomExecutionContext(system, "facade.repository.dispatcher")

  /**
   * Case class used to contain the current repository state. (Returned by calls to the ping endpoint)
   * @param facadeVersion the current running version of the facade
   * @param systemIdentifier the user-defined system identifier for the running instance of the facade
   * @param schemaVersion the underlying OTCS schema information (if available)
   */
  case class RepositoryState (facadeVersion: String = SystemConstants.AppVersion,
                              systemIdentifier : String,
                              schemaVersion : String)

  /**
   * A [[Writes]] implementation for the serialisation of [[RepositoryState]]
   */
  implicit val repositoryStateWrites: Writes[RepositoryState] = new Writes[RepositoryState]{
    override def writes(o: RepositoryState): JsValue ={
      Json.obj(
        "systemIdentifier" -> o.systemIdentifier,
        "facadeVersion" -> SystemConstants.AppVersion,
        "otcsSchemaVersion" -> o.schemaVersion
      )
    }
  }
}
