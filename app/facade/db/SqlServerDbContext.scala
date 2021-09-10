package facade.db

import anorm.SqlParser.scalar
import anorm._
import facade.db.SqlServerDbContext.schemaVersionSql
import facade.{FacadeConfig, LogNames}
import play.api.cache.AsyncCacheApi
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Default implementation of the [[DbContext]] trait
 */
@Singleton
class SqlServerDbContext @Inject()(configuration: Configuration, lifecycle: ApplicationLifecycle, cache: AsyncCacheApi, db: Database,
                                   dbExecutionContext: DbExecutionContext)
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

  /**
   * Gets the version of the underlying repository (OTCS) schema and returns it as a string
   *
   * @return the current version of the underlying repository schema
   */
  override def SchemaVersion(): Future[String] = {
    implicit val ec: ExecutionContext = dbExecutionContext
    Future {
      db.withConnection { implicit c =>
        val version: Option[String] = {
          schemaVersionSql as scalar[String].singleOpt
        }
        version match {
          case Some(v) => v.replace("{", "").replace("}", "").replace(',', '.')
          case _ => "Failed to get version"
        }
      }
    }
  }
}

/**
 * Companion object for [[SqlServerDbContext]]
 */
object SqlServerDbContext {

  /**
   * Sql to retrieve the underlying OTCS schema version information
   */
  lazy val schemaVersionSql: SimpleSql[Row] = SQL"select IniValue from kini where IniKeyword = 'DatabaseVersion'"

}
