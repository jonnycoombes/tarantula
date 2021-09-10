package facade

import play.api.libs.json.{JsValue, Json, Writes}

/**
 * Top level package object for the repository package.  Statics, helpers etc...go here
 */
package object repository {

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
