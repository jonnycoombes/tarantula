package facade.db

import akka.parboiled2.CharPredicate.General
import anorm.SqlParser.scalar
import anorm._
import facade.db.SqlServerDbContext.{GeneralQueries, NodeQueries}
import facade.{FacadeConfig, LogNames}
import play.api.cache.{NamedCache, SyncCacheApi}
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import java.net.URLDecoder
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, blocking}
import scala.language.postfixOps

/**
 * Case class used to track state during recursive path resolution calls
 *
 * @param lastDetails the [[NodeCoreDetails]] for the last element in the prefix
 * @param prefix      the prefix of the path processed so far
 */
case class PathResolutionState(lastDetails: Option[NodeCoreDetails], prefix: String)

/**
 * Default implementation of the [[DbContext]] trait
 */
@Singleton
class SqlServerDbContext @Inject()(configuration: Configuration,
                                   lifecycle: ApplicationLifecycle,
                                   @NamedCache("db-cache") cache: SyncCacheApi,
                                   db: Database,
                                   implicit val dbExecutionContext: DbExecutionContext)
  extends
    DbContext {

  /**
   * The logger used by this class
   */
  private val log = Logger(LogNames.DbContextLogger)

  /**
   * The current [[FacadeConfig]] instance
   */
  private val facadeConfig = FacadeConfig(configuration)

  queryChildByName(None, "Enterprise") match {
    case Right(details) =>
      log.trace("Caching Enterprise details")
      cache.set("Enterprise", details, facadeConfig.idCacheLifetime)
    case Left(ex) =>
      throw ex
  }

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
            implicit val config: FacadeConfig = facadeConfig
            Right(GeneralQueries.schemaVersionSql as scalar[String].single)
          }
        } catch {
          case e: Throwable => Left(e)
        }
      }
    }
  }

  /**
   * Internal function which does the actual db query in order to try and lookup details based on an optional parentId and name
   *
   * @param details an option containing the parent details
   * @param name     the name of the node to lookup
   * @return A [[DbContextResult]]
   */
  private def queryChildByName(details: Option[NodeCoreDetails], name: String): DbContextResult[NodeCoreDetails] = {
    try {
      db.withConnection { implicit c =>
        implicit val config: FacadeConfig = facadeConfig
        val parentId = details.fold(-1L)(d => deriveParentId(d))
        val results = NodeQueries.nodeDetailsByNameSql.on("p1" -> parentId).on("p2" -> name).as(nodeCoreDetailsParser.*)
        if (results.isEmpty) {
          Left(new Throwable("No node with that name exists"))
        } else {
          Right(results.head)
        }
      }
    } catch {
      case t: Throwable =>
        log.error(s"Query breakdown '${t.getMessage}")
        Left(t)
    }
  }

  /**
   * Local function for determining the correct parentId used to drive the query
   *
   * @param details an instance of [[NodeCoreDetails]]
   * @return
   */
  @inline def deriveParentId(details: NodeCoreDetails): Long = {
    if (details.isAlias) {
      details.originDataId
    } else if (details.isVolume) {
      -details.dataId
    } else {
      details.dataId
    }
  }

  /**
   * Perform a cache-aware lookup for a given node, based on a path
   *
   * @param path the path to resolve
   * @return a [[Future]]
   */
  private def resolvePath(path: List[String]): Future[Option[NodeCoreDetails]] = {

    if (path.isEmpty) Future.successful(None)
    Future {
      blocking {
        val prefix = new ListBuffer[String]
        var result: Option[NodeCoreDetails] = None
        for (segment <- path) {
          prefix += segment
          val prefixCacheKey = prefix.toList.mkString("/")
          cache.get[NodeCoreDetails](prefixCacheKey) match {
            case Some(details) =>
              log.trace(s"Prefix cache *hit* for '${prefixCacheKey}'")
              result = Some(details)
            case None =>
              log.trace(s"Prefix cache *miss* for '${prefixCacheKey}'")
              queryChildByName(result, segment) match {
                case Right(details) =>
                  log.trace(s"Setting prefix cache entry for '${prefixCacheKey}' [${details}]")
                  cache.set(prefixCacheKey, details, facadeConfig.idCacheLifetime)
                  result = Some(details)
                case Left(t) =>
                  result = None
              }
          }
        }
        result
      }
    }
  }

  /**
   * Recurses a series of queries in order to retrieve the core details about a node identified by a path relative to a root
   *
   * @param path a list of path elements, which when combined form a complete relative path of the form A/B/C
   * @return a [[Future]] containing a [[DbContextResult]] which can either be a [[NodeCoreDetails]] instance or a [[Throwable]]
   */
  override def queryNodeCoreDetailsByPath(path: List[String]): Future[DbContextResult[NodeCoreDetails]] = {
    val combined = path.mkString("/")
    log.trace(s"Lookup for path '$combined'")
    cache.get[NodeCoreDetails](combined) match {
      case Some(details) =>
        log.trace(s"Full cache *hit* for '${combined}'")
        Future.successful(Right(details))
      case None =>
        log.trace(s"Full cache *miss* for '${combined}'")
        resolvePath(path) map {
          case Some(details) =>
            Right(details)
          case None =>
            Left(new Throwable(s"Could not resolve path '${URLDecoder.decode(combined, "UTF-8")}'"))
        }
    }
  }

  /**
   * Returns a list of the child ids for a given node
   *
   * @param details the [[NodeCoreDetails]] for the parent
   * @return a [[Future]] containing a [[DbContextResult]] which can either be a list of node ids or a [[Throwable]]
   */
  override def queryChildrenCoreDetails(details: NodeCoreDetails): Future[DbContextResult[List[NodeCoreDetails]]] = {
    Future {
      blocking {
        try {
          db.withConnection { implicit c =>
            implicit val config: FacadeConfig = facadeConfig
            val parentId = deriveParentId(details)
            Right(NodeQueries.nodeChildrenById.on("p1" -> parentId).as(nodeCoreDetailsParser.*))
          }
        } catch {
          case t: Throwable =>
            log.error(s"Query breakdown '${t.getMessage}")
            Left(t)
        }
      }
    }
  }

  /**
   * Queries for the [[NodeCoreDetails]] of a node given an id
   *
   * @param id the id of the node to search for
   * @return a result hopefully containing a [[NodeCoreDetails]] instance
   */
  override def queryNodeCoreDetailsById(id: Long): Future[DbContextResult[NodeCoreDetails]] = {
    Future {
      blocking {
        try {
          db.withConnection { implicit connection =>
            implicit val config: FacadeConfig = facadeConfig
            Right(NodeQueries.nodeDetailsById.on("p1" -> id).as(nodeCoreDetailsParser.single))
          }
        } catch {
          case t: Throwable =>
            log.error(s"Query breakdown '${t.getMessage}'")
            Left(t)
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

  object GeneralQueries {
    /**
     * SQL to retrieve the underlying OTCS schema version information from the kini table
     */
    def schemaVersionSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(s"select IniValue from ${config.dbSchema}.KIni where " +
      s"IniKeyword = 'DatabaseVersion'")

  }

  object NodeQueries {



    /**
     * SQL used to lookup a node based on name and parent id. Make sure that only positive DataID values are returned in order to cater for
     * volumes, which have two different reciprocal values (one +ve, one -ve)
     */
    def nodeDetailsByNameSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""select ParentID, DataID, VersionNum, Name, SubType, OriginDataID, CreateDate, ModifyDate
                                                            from ${config.dbSchema}.DTreeCore
                                                              where ParentID = {p1} and Name = {p2} and DataID > 0""")

    def nodeDetailsById(implicit config: FacadeConfig) : SimpleSql[Row] = SQL(
      s"""
         |select ParentId, DataID, VersionNum, Name, SubType, OriginDataID, CreateDate, ModifyDate
         |  from ${config.dbSchema}.DTreeCore
         |    where DataID = {p1}
         |""".stripMargin
    )

    /**
     * SQL used to retrieve a list of child nodes for a given parent id
     */
    def nodeChildrenById(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""
         |select ParentID, DataID, VersionNum, Name, SubType, OriginDataID, CreateDate, ModifyDate
         |   from ${config.dbSchema}.DTreeCore
         |     where ParentID = {p1}
         |""".stripMargin)

  }

}
