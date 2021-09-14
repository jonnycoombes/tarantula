package facade.db

import anorm.SqlParser.scalar
import anorm._
import facade.db.SqlServerDbContext.{nodeCoreDetailsParser, nodeDetailsByNameSql, schemaVersionSql}
import facade.{FacadeConfig, LogNames}
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{Future, blocking}

/**
 * Default implementation of the [[DbContext]] trait
 */
@Singleton
class SqlServerDbContext @Inject()(configuration: Configuration,
                                   lifecycle: ApplicationLifecycle,
                                   @NamedCache("db-cache") cache: AsyncCacheApi,
                                   db: Database,
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
        try {
          db.withConnection { implicit c =>
            Right(schemaVersionSql as scalar[String].single)
          }
        }catch {
          case e : Throwable => Left(e)
        }
      }
    }
  }

  /**
   * Executes a query (or series of queries) in order to retrieve the core details about a given node
   *
   * @param parentId an optional parentId for the node. If None, then the id is taken to be top-level
   * @param name     the name of the node to look for
   * @return a [[Future]] containing a [[DbContextResult]]
   */
  override def queryNodeDetailsByName(parentId: Option[Long], name: String): Future[DbContextResult[Long]] = {
    Future{
      blocking {
        lookupNode(parentId, name)
      }
    }
  }

  /**
   * Internal function which does the actual db query in order to try and lookup details based on an optional parentId and name
   * @param parentId optional parentId
   * @param name the name of the node to lookup
   * @return A [[DbContextResult]]
   */
  private def lookupNode(parentId : Option[Long], name : String) : DbContextResult[Long] = {
    try {
      db.withConnection { implicit c =>
        val results = parentId match {
          case Some(id) => nodeDetailsByNameSql.on("p1" -> parentId).on("p2" -> name).as(nodeCoreDetailsParser.*)
          case None => nodeDetailsByNameSql.on("p1" -> -1).on("p2" -> name).as(nodeCoreDetailsParser.*)
        }
        if (results.isEmpty){
          Left(new Throwable("No node with that name exists"))
        }else{
          Right(results.head.dataId)
        }
      }
    }catch {
      case e : Throwable => Left(e)
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

  /**
   * SQL used to lookup a node based on name and parent id
   */
  lazy val nodeDetailsByNameSql : SimpleSql[Row] = SQL("""select ParentID, DataID, Name, OriginDataID, SubType
                                                            from DTreeCore
                                                              where ParentID = {p1} and Name = {p2}""")

  /**
   * Parser for handling DTreeCore subset
   */
  lazy val nodeCoreDetailsParser : RowParser[NodeCoreDetails] = Macro.parser[NodeCoreDetails]("ParentID", "DataID", "Name", "OriginDataID", "SubType")


}
