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
import scala.concurrent.{Future, blocking}
import scala.util.control.Exception.allCatch
import scala.util.{Failure, Success, Try}

/**
 * Default implementation of the [[DbContext]] trait
 */
@Singleton
class SqlServerDbContext @Inject()(configuration: Configuration, lifecycle: ApplicationLifecycle, cache: AsyncCacheApi, db: Database,
                                   implicit val dbExecutionContext: DbExecutionContext)
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
      log.debug("SqlServerDbContext stop hook called")
    })
  }

  /**
   * Gets the version of the underlying repository (OTCS) schema and returns it as a string
   *
   * @return the current version of the underlying repository schema
   */
  override def schemaVersion(): Future[DbContextResult[String]] = {
    Future {
      blocking {
        allCatch.withTry {
          db.withConnection { implicit c =>
            schemaVersionSql as scalar[String].single
          }
        } match {
          case Success(value) => Right(value)
          case Failure(ex) => Left(ex)
        }
      }
    }
  }
}

/**
 * Companion object for [[SqlServerDbContext]]. Convenient place to store any Anorm parsers and SQL statement objects utilised within the
 * class implementation of [[SqlServerDbContext]]
 */
object SqlServerDbContext {

  /**
   * SQL to retrieve the underlying OTCS schema version information from the kini table
   */
  lazy val schemaVersionSql: SimpleSql[Row] = SQL"select IniValue from kini where IniKeyword = 'DatabaseVersion'"


}
