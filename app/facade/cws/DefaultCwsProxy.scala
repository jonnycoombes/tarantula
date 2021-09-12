package facade.cws

import facade.{FacadeConfig, LogNames}
import facade.db.DbContext
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.inject.ApplicationLifecycle

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Default implementation of the [[CwsProxy]] trait
 * @param configuration current [[Configuration]] instance
 * @param lifecycle in order to add any lifecycle hooks
 * @param cache in case a cache is required
 */
@Singleton
class DefaultCwsProxy @Inject()(configuration: Configuration,
                               lifecycle: ApplicationLifecycle,
                               cache: AsyncCacheApi, implicit val cwsProxyExecutionContext: CwsProxyExecutionContext) extends CwsProxy {

  /**
   * The log used by the repository
   */
  private val log = Logger(LogNames.CwsProxyLogger)
  /**
   * The current facade configuration
   */
  private val facadeConfig = FacadeConfig(configuration)

  lifecycle.addStopHook { () =>
    Future.successful({
      log.debug("CwsProxy stop hook called")
    })
  }




}
