package facade.repository

import facade._
import facade.cws.CwsProxy
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
 * @param cache         the application level cache (default backing implementation is ehcache)
 *
 */
@Singleton
class DefaultRepository @Inject()(configuration: Configuration,
                                  lifecycle: ApplicationLifecycle,
                                  cache: AsyncCacheApi,
                                  dbContext: DbContext,
                                  cwsProxy: CwsProxy,
                                  implicit val repositoryExecutionContext: RepositoryExecutionContext)
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
      log.debug("Repository stop hook called")
    })
  }

  /**
   * Gets the current repository state information
   *
   * @return an instance of [[RepositoryState]]
   */
  //noinspection DuplicatedCode
  override def repositoryState(): Future[RepositoryState] = {

    val cwsServerInfo = cwsProxy.serverInfo()
    val dbSchemaVersion = dbContext.schemaVersion()
    val serviceHealth = for {
      a <- cwsServerInfo
      b <- dbSchemaVersion
    } yield (a, b)

    serviceHealth map { s =>
      val serverInfo = s._1
      val schemaVersion = s._2
      if (serverInfo.isRight && schemaVersion.isRight){
        RepositoryState(facadeConfig, serverInfo.toOption.get, schemaVersion.toOption.get)
      }else if (serverInfo.isRight && schemaVersion.isLeft) {
        RepositoryState(facadeConfig, serverInfo.toOption.get)
      }else if (serverInfo.isLeft && schemaVersion.isLeft){
        RepositoryState(facadeConfig)
      }else{
        RepositoryState(facadeConfig, schemaVersion.toOption.get)
      }
    }

  }
}
