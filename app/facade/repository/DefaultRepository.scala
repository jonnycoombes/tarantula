package facade.repository

import facade._
import facade.db.DbContext
import play.api.cache.AsyncCacheApi
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * The default implementation of the [[Repository]] trait, which uses CWS bindings and direct (read-only) DB connections in order to
 * provide the required functionality. Most repository functionality is based around a path abstraction, which is translated to a
 * location within the underlying OTCS repo. In order to improve performance, two things happen:
 *  1. The path is translated to an underlying node id through direct SQL queries to the relevant schema tables
 *  1. Path node IDs are cached between subsequent requests
 *
 * @constructor instantiates a new instance of the repository
 * @param configuration the current application [[Configuration]]
 * @param lifecycle     a lifecycle hook so that any shutdown clean-up can be carried out
 * @param cache the application level cache (default backing implementation is ehcache)
 *
 */
@Singleton
class DefaultRepository @Inject()(configuration: Configuration, lifecycle: ApplicationLifecycle, cache: AsyncCacheApi, dbContext : DbContext)
  extends Repository {

  /**
   * The log used by the repository
   */
  private val log = Logger(LogNames.RepositoryLogger)

  /**
   * The current facade configuration
   */
  private val facadeConfig = FacadeConfig(configuration)

  log.debug("Initialising repository")

  lifecycle.addStopHook { () =>
    Future.successful({
      log.debug("Cleaning up repository")
    })
  }

  /**
   * Gets the current repository state information
   *
   * @return an instance of [[RepositoryState]]
   */
  override def repositoryState(): Future[RepositoryState] = ???
}
