package facade

import akka.actor.ActorSystem
import com.opentext.cws.admin.ServerInfo
import facade.repository.Status.RepositoryStatus
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json.{JsValue, Json, Writes}

import javax.inject.{Inject, Singleton}
import scala.language.implicitConversions

/**
 * Top level package object for the repository package.  Statics, types, helpers etc...go here
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
   * Contains the current repository state, including information as to the availability of the underlying DB and OTCS instance
   * @param version the current version of the Facade
   * @param schemaVersion the current underlying schema version (OTCS)
   * @param systemIdentifier the user-defined system identifier
   * @param status the current [[Status]]
   * @param message any useful messages to be relayed back to the client
   * @param serverVersion the OTCS server version
   * @param serverLanguage the OTCS server configured language
   * @param serverDateTime the current OTCS server date and time
   */
  case class RepositoryState (version: String,
                              schemaVersion : Option[String],
                              systemIdentifier : String,
                              status : RepositoryStatus = Status.Ok,
                              message : Option[String] = None,
                              serverVersion : Option[String] = None,
                              serverLanguage : Option[String] = None,
                              serverDateTime : Option[String] = None)

  object RepositoryState {
    def apply(config : FacadeConfig, serverInfo : ServerInfo, schemaVersion : String): RepositoryState ={
      RepositoryState(version = config.version,
        schemaVersion = Some(schemaVersion),
        systemIdentifier = config.systemIdentifier,
        serverVersion= Some(serverInfo.getServerVersion),
        serverLanguage = Some(serverInfo.getLanguageCode),
        serverDateTime = Some(serverInfo.getServerDateTime.toString))
    }
    def apply(config: FacadeConfig, serverInfo : ServerInfo) : RepositoryState ={
      RepositoryState(version = config.version,
        schemaVersion = None,
        status = Status.NoDb,
        message = Some("No DB schema information available - check RDBMS service status"),
        systemIdentifier = config.systemIdentifier,
        serverVersion = Some(serverInfo.getServerVersion),
        serverLanguage = Some(serverInfo.getLanguageCode),
        serverDateTime = Some(serverInfo.getServerDateTime.toString))
    }
    def apply(config: FacadeConfig, schemaVersion: String) : RepositoryState = {
      RepositoryState(version = config.version,
        schemaVersion = Some(schemaVersion),
        status = Status.NoOtcs,
        message = Some("No OTCS server information available - check OTCS service status"),
        systemIdentifier = config.systemIdentifier)
    }
    def apply(config : FacadeConfig, t : Throwable): RepositoryState = {
      RepositoryState(version = config.version,
        schemaVersion = None,
        status = Status.Exception,
        message = Some(t.getMessage),
        systemIdentifier = config.systemIdentifier)
    }

    def apply(config : FacadeConfig) : RepositoryState = {
      RepositoryState(version = config.version,
        schemaVersion = None,
        status = Status.NoOtcsOrDb,
        message = Some("No DB or OTCS information available - check backend service statuses"),
        systemIdentifier = config.systemIdentifier)
    }
  }

  object Status extends Enumeration {
    type RepositoryStatus = Value
    val Ok, Exception, NoDb, NoOtcs, NoOtcsOrDb  = Value
  }

  /**
   * A [[Writes]] implementation for the serialisation of [[RepositoryState]]
   */
  implicit val repositoryStateWrites: Writes[RepositoryState] = new Writes[RepositoryState]{
    override def writes(o: RepositoryState): JsValue ={
      Json.obj(
        "version" -> SystemConstants.AppVersion,
        "schemaVersion" -> o.schemaVersion.fold("Unknown")(s => s),
        "systemIdentifier" -> o.systemIdentifier,
        "status" -> o.status.toString,
        "message" -> o.message.fold("Nothing to report")(s => s),
        "otcsServerVersion" -> o.serverVersion.fold("Unknown")(s => s),
        "otcsServerLanguage" -> o.serverLanguage.fold("Unknown")(s => s),
        "otcsServerDateTime" -> o.serverDateTime.fold("Unknown")(s => s)
      )
    }
  }

  /**
   * The repository path separation character
   */
  lazy val RepositoryPathSeparator = '/'

  /**
   * Implicit conversion to move between a string and [[RepositoryPath]]
   * @param s a string of the form A/B/C/etc
   * @return a [[RepositoryPath]]
   */
  implicit def splitReppositoryPath(s : String) : List[String] = {
    if (s.isEmpty) {
        List.empty[String]
    } else {
      s.split(RepositoryPathSeparator).toList
    }
  }

  /**
   * Returned by some repository methods (e.g. content downloads)
   * @param file a temporary file location
   * @param length the length of the file
   * @param contentType the content/MIME type of the file
   */
  case class FileInformation(file : java.io.File, length : Long, contentType : String)

}
