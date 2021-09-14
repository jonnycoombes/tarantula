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
     * A dedicated log for the Cws proxy layer
     */
    lazy val CwsProxyLogger = "facade-cws-proxy"

    /**
     * A dedicated log for timings information (if enabled)
     */
    lazy val TimingsLogger = "facade-timings"

  }

  /**
   * Object containing all the configuration defaults for the facade
   */
  object SystemConstants {

    /**
     * A default system identifier
     */
    lazy val SystemIdentifier = "hyperion"

    /**
     * The current application version
     */
    lazy val AppVersion = "1.0.1"

    /**
     * A default CWS user account
     */
    lazy val DefaultCwsUser = "Admin"

    /**
     * A default CWS password
     */
    lazy val DefaultCwsPassword = "livelink"

    /**
     * The default time in seconds before an authentication token is expired from the cache
     */
    lazy val DefaultTokenCacheExpiration = 30

  }

  /**
   * The current configuration for the facade.  This is bound to the facade configuration settings section within the application.conf file.
   * The configuration fields are largely documented within the facade.conf file.
   */
  case class FacadeConfig(systemIdentifier: String,
                          version: String,
                          cwsUser : Option[String],
                          cwsPassword : Option[String])

  /**
   * Companion object for the [[FacadeConfig]] case class. Includes an apply method which allows a configuration to be derived from a
   * given [[play.api.Configuration]]
   */
  object FacadeConfig {
    def apply(config: Configuration): FacadeConfig = {
      FacadeConfig(
        systemIdentifier = config.getOptional[String]("facade.system.identifier").getOrElse(SystemConstants.SystemIdentifier),
        version = SystemConstants.AppVersion,
        cwsUser = config.getOptional[String]("facade.cws.user"),
        cwsPassword = config.getOptional[String]("facade.cws.password")
      )
    }
  }


}
