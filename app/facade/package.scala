import com.typesafe.config.Config
import play.api.ConfigLoader

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
    def MainLog() : String = {
      MAIN_LOG
    }

    /**
     * @return the [[facade.repository.Repository]] logger name
     */
    def RepositoryLog() : String = {
      REPOSITORY_LOG
    }

  }

  /**
   * Object containing all the configuration defaults for the facade
   */
  object ConfigDefaults {
    /**
     * The current application version
     */
    private val APP_VERSION = "1.0.1"

    /**
     * @return the current application version
     */
    def Version(): String = {
      APP_VERSION
    }

  }

  /**
   * The current configuration for the facade.  This is bound to the facade configuration settings section within the application.conf file
   */
  case class FacadeConfig(version: String)

  object FacadeConfig {
    implicit val configLoader:ConfigLoader[FacadeConfig]= new ConfigLoader[FacadeConfig]{
      override def load(rootConfig: Config, path: String): FacadeConfig = {
      val config = rootConfig.getConfig(FACADE_CONFIG_SECTION)
        FacadeConfig(
          version = ConfigDefaults.Version()
        )
      }
    }
  }


}
