import com.typesafe.config.ConfigObject
import facade.db.DbContext
import play.api.Configuration

import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.language.postfixOps

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
    lazy val DefaultTokenCacheExpiration: FiniteDuration = 15 minutes

  }

  /**
   * The current configuration for the facade.  This is bound to the facade configuration settings section within the application.conf file.
   * The configuration fields are largely documented within the facade.conf file.
   */
  case class FacadeConfig(systemIdentifier: String,
                          version: String,
                          cwsUser : Option[String],
                          cwsPassword : Option[String],
                          pathExpansions : mutable.Map[String, String],
                          tokenCacheLifetime : Duration,
                          idCacheLifetime : Duration,
                          jsonCacheLifetime : Duration)

  /**
   * Companion object for the [[FacadeConfig]] case class. Includes an apply method which allows a configuration to be derived from a
   * given [[play.api.Configuration]]
   */
  object FacadeConfig {

    private def mapPathExpansions(config : Configuration) : mutable.Map[String, String] = {
      val mappedExpansions : mutable.Map[String, String] = mutable.Map[String, String]()
      val expansions : Iterable[ConfigObject] = config.underlying.getObjectList("facade.path.expansions").asScala
          for (item <- expansions){
            mappedExpansions.addOne((item.keySet().toArray()(0).toString,
                                    item.entrySet().iterator().next().getValue.unwrapped().toString))
          }
      mappedExpansions
    }

    def apply(config: Configuration): FacadeConfig = {

      FacadeConfig(
        systemIdentifier = config.getOptional[String]("facade.system.identifier").getOrElse(SystemConstants.SystemIdentifier),
        version = SystemConstants.AppVersion,
        cwsUser = config.getOptional[String]("facade.cws.user"),
        cwsPassword = config.getOptional[String]("facade.cws.password"),
        pathExpansions = mapPathExpansions(config),
        tokenCacheLifetime = config.getOptional[Duration]("facade.token.cache.lifetime").getOrElse(SystemConstants.DefaultTokenCacheExpiration),
        idCacheLifetime = config.getOptional[Duration]("facade.id.cache.lifetime").getOrElse(Duration.Inf),
        jsonCacheLifetime = config.getOptional[Duration]("facade.json.cache.lifetime").getOrElse(Duration.Inf),
      )
    }
  }


}
