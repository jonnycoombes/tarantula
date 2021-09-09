import com.typesafe.config.Config
import play.api.{ConfigLoader, Configuration}

import scala.annotation.tailrec

package object facade {

  /**
   * The name of the section within the configuration file containing facade configuration information
   */
  lazy val FACADE_CONFIG_SECTION = "facade"

  object LogDefaults {
    /**
     * The main facade log
     */
    private val MAIN_LOG = "facade"

    /**
     * A dedicated log for [[facade.repository.Repository]] implementations
     */
    private val REPOSITORY_LOG = "repository"

    /**
     * @return the main logger name
     */
    def MainLog(): String = {
      MAIN_LOG
    }

    /**
     * @return the [[facade.repository.Repository]] logger name
     */
    def RepositoryLog(): String = {
      REPOSITORY_LOG
    }

  }

  /**
   * Object containing all the configuration defaults for the facade
   */
  object ConfigDefaults {

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
      val systemId = config.getOptional[String]("facade.system.identifier").getOrElse(ConfigDefaults.SystemIdentifier)
      FacadeConfig(
        systemIdentifier = systemId,
        version = ConfigDefaults.AppVersion
      )
    }
  }


}
