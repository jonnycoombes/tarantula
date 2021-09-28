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
   * Loads a [[NodeDetails]] case class from the database based on a given node id
   *
   * @param id     the id of the node to retrieve the [[NodeDetails]] for
   * @param config an implicit config which provides the schema prefix to use
   * @return
   */
  private def loadNodeDetails(id: Long)(implicit config: FacadeConfig): NodeDetails = {
    try {
      log.trace(s"loadNodeDetails id=$id")
      db.withConnection { implicit c =>
        val core = NodeQueries.nodeCoreByIdSql.on("p1" -> id).as(nodeCoreDetailsParser.single)
        val versions = NodeQueries.nodeVersionsByIdSql.on("p1" -> id).as(nodeVersionDetailsParser.*)
        val attributes = NodeQueries.nodeAttributesByIdSql.on("p1" -> id).as(nodeAttributeDetailsParser.*)
        NodeDetails(core, versions, attributes)
      }
    } catch {
      case t: Throwable =>
        log.error(s"Query breakdown '${t.getMessage}'")
        throw t
    }
  }

  /**
   * Loads a [[NodeDetails]] case class from the database based on a given parent id and node name
   *
   * @param parentId the parent id
   * @param name     the name of the node to searc for
   * @param config   an implicit config which provides the schema prefix to use
   * @return
   */
  private def loadNodeDetails(parentId: Long, name: String)(implicit config: FacadeConfig): NodeDetails = {
    try {
      log.trace(s"loadNodeDetails parentId=$parentId, name=$name")
      db.withConnection { implicit c =>
        val core = NodeQueries.nodeCoreByNameSql.on("p1" -> parentId, "p2" -> name).as(nodeCoreDetailsParser.single)
        val versions = NodeQueries.nodeVersionsByIdSql.on("p1" -> core.dataId).as(nodeVersionDetailsParser.*)
        val attributes = NodeQueries.nodeAttributesByIdSql.on("p1" -> core.dataId).as(nodeAttributeDetailsParser.*)
        NodeDetails(core, versions, attributes)
      }
    } catch {
      case t: Throwable =>
        log.error(s"Query breakdown '${t.getMessage}'")
        throw t
    }
  }

  /**
   * Internal function which does the actual db query in order to try and lookup details based on an optional parentId and name
   *
   * @param details an option containing the parent details
   * @param name    the name of the node to lookup
   * @return A [[DbContextResult]]
   */
  private def queryChildByName(details: Option[NodeDetails], name: String): DbContextResult[NodeDetails] = {
    try {
      implicit val config = facadeConfig
      val parentId = details.fold(-1L)(d => deriveParentId(d))
      Right(loadNodeDetails(parentId, name))
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
  @inline def deriveParentId(details: NodeDetails): Long = {
    if (details.core.isAlias) {
      details.core.originDataId
    } else if (details.core.isVolume) {
      -details.core.dataId
    } else {
      details.core.dataId
    }
  }

  /**
   * Perform a cache-aware lookup for a given node, based on a path
   *
   * @param path the path to resolve
   * @return a [[Future]]
   */
  private def resolvePath(path: List[String]): Future[Option[NodeDetails]] = {

    if (path.isEmpty) Future.successful(None)
    Future {
      blocking {
        val prefix = new ListBuffer[String]
        var result: Option[NodeDetails] = None
        for (segment <- path) {
          prefix += segment
          val prefixCacheKey = prefix.toList.mkString("/")
          cache.get[NodeDetails](prefixCacheKey) match {
            case Some(details) =>
              log.trace(s"Prefix cache *hit* for '$prefixCacheKey'")
              result = Some(details)
            case None =>
              log.trace(s"Prefix cache *miss* for '$prefixCacheKey'")
              queryChildByName(result, segment) match {
                case Right(details) =>
                  log.trace(s"Setting prefix cache entry for '$prefixCacheKey' [$details]")
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
  override def queryNodeDetailsByPath(path: List[String]): Future[DbContextResult[NodeDetails]] = {
    val combined = path.mkString("/")
    log.trace(s"Lookup for path '$combined'")
    cache.get[NodeDetails](combined) match {
      case Some(details) =>
        log.trace(s"Full cache *hit* for '$combined'")
        Future.successful(Right(details))
      case None =>
        log.trace(s"Full cache *miss* for '$combined'")
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
  override def queryChildrenDetails(details: NodeDetails): Future[DbContextResult[List[NodeDetails]]] = {
    Future {
      blocking {
        try {
          db.withConnection { implicit c =>
            implicit val config: FacadeConfig = facadeConfig
            val parentId = deriveParentId(details)
            val children: List[NodeCoreDetails] = NodeQueries.nodeChildrenByIdSql.on("p1" -> parentId).as(nodeCoreDetailsParser.*)
            Right(children map { child =>
              loadNodeDetails(child.dataId)
            })
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
  override def queryNodeDetailsById(id: Long): Future[DbContextResult[NodeDetails]] = {
    Future {
      blocking {
        try {
          implicit val config: FacadeConfig = facadeConfig
          Right(loadNodeDetails(id))
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
    def nodeCoreByNameSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""select ParentID, DataID, VersionNum, Name, SubType, OriginDataID, CreateDate, ModifyDate
                                                            from ${config.dbSchema}.DTreeCore
                                                              where ParentID = {p1} and Name = {p2} and DataID > 0""")

    /**
     * SQL to retrieve the basic details associated with a given node, based on the DataID
     *
     * @param config an implicit [[FacadeConfig]] to get the correct schema prefix
     * @return
     */
    def nodeCoreByIdSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""
         |select ParentId, DataID, VersionNum, Name, SubType, OriginDataID, CreateDate, ModifyDate
         |  from ${config.dbSchema}.DTreeCore
         |    where DataID = {p1}
         |""".stripMargin
    )

    /**
     * SQL used to retrieve a list of child nodes for a given parent id
     *
     * @param config an implicit [[FacadeConfig]] to get the correct schema prefix
     */
    def nodeChildrenByIdSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""
         |select ParentID, DataID, VersionNum, Name, SubType, OriginDataID, CreateDate, ModifyDate
         |   from ${config.dbSchema}.DTreeCore
         |     where ParentID = {p1}
         |""".stripMargin)

    /**
     * SQL used to retrieve a list of category/attribute pairs for a given node
     *
     * @param config an implicit [[FacadeConfig]] used to get the correct schema prefix
     * @return
     */
    def nodeAttributesByIdSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""
         |select c.Category, c.Attribute,
         |	b.AttrType, b.ValDate, b.ValInt, b.ValLong, b.ValReal, b.ValStr
         |from ${config.dbSchema}.DTreeCore a inner join ${config.dbSchema}.LLAttrData b on a.DataID = b.ID
         |inner join
         |	( select *
         |	from ${config.dbSchema}.Facade_Attributes) c
         |		on b.DefID = c.CategoryId
         |		and b.DefVerN = c.CategoryVersion
         |		and b.AttrID = c.AttributeIndex
         |	where a.DataID = {p1}
         |		and b.VerNum = a.VersionNum
         |		order by c.Category, c.AttributeIndex
         |""".stripMargin
    )

    /**
     * SQL user to retrieve the version details for a given node, based on id
     *
     * @param config an implicit [[FacadeConfig]] used to get the correct schema prefix
     * @return
     */
    def nodeVersionsByIdSql(implicit config: FacadeConfig): SimpleSql[Row] = SQL(
      s"""
         | select  VersionID, Version, VerCDate, VerMDate, FileCDate, FileMDate, FileName, DataSize, MimeType
         | from ${config.dbSchema}.DVersData
         |  where DocID = {p1}
         |""".stripMargin
    )

  }

}
