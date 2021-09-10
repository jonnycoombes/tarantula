import facade.db.DbContext
import play.api.Configuration

/**
 * Top level package object for the facade package. Configuration related top level objects and classes go in here,
 * along with anything related to logging/general cross-cutting concerns
 */
package object facade {

  object LogNames {
    /**
     * The main facade log
     */
    lazy val MainLogger = "facade"

    /**
     * A dedicated log for [[facade.repository.Repository]] implementations
     */
    lazy val RepositoryLogger = "facade-repository"

    /**
     * A dedicated log for [[DbContext]]
     */
    lazy val DbContextLogger = "facade-db-context"

    /**
     * A dedicated log for timings information (if enabled)
     */
    lazy val TimingsLogger = "facade-timings"

  }

  /**
   * Object containing all the configuration defaults for the facade
   */
  object SystemConstants {

    lazy val SystemIdentifier = "hyperion"

    /**
     * The current application version
     */
    lazy val AppVersion = "1.0.1"

  }

  /**
   * The current configuration for the facade.  This is bound to the facade configuration settings section within the application.conf file
   */
  case class FacadeConfig(systemIdentifier: String, version: String)

  /**
   * Companion object for the [[FacadeConfig]] case class. Includes an apply method which allows a configuration to be derived from a
   * given [[play.api.Configuration]]
   */
  object FacadeConfig {
    def apply(config: Configuration): FacadeConfig = {
      val systemId = config.getOptional[String]("facade.system.identifier").getOrElse(SystemConstants.SystemIdentifier)
      FacadeConfig(
        systemIdentifier = systemId,
        version = SystemConstants.AppVersion
      )
    }
  }


}
