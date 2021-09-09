package facade.db

import facade.{FacadeConfig, LogNames}
import play.api.cache.AsyncCacheApi
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.concurrent.Future

/**
 * Default implementation of the [[DbContext]] trait
 */
class SqlServerDbContext(configuration: Configuration, lifecycle: ApplicationLifecycle, cache: AsyncCacheApi, db : Database)
  extends
  DbContext {

  /**
   * The logger used by this class
   */
  private val log = Logger(LogNames.RepositoryLogger)

  /**
   * The current [[FacadeConfig]] instance
   */
  private val facadeConfig = FacadeConfig(configuration)

  lifecycle.addStopHook { () =>
    Future.successful({
      log.debug("Cleaning up DB context")
    })
  }

}
