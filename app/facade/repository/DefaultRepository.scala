package facade.repository

import facade._
import facade.cws.{CwsProxy, DownloadedContent}
import facade.db.DbContext
import facade.db.Model._
import play.api.cache.{NamedCache, SyncCacheApi}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.{Configuration, Logger}

import java.net.URLDecoder
import java.nio.file.Path
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
 * @param jsonCache     cache for storing rendered JSON artifacts
 * @param dbContext     the [[DbContext]] service
 * @param cwsProxy      the [[CwsProxy]] service
 *
 */
@Singleton
class DefaultRepository @Inject()(configuration: Configuration,
                                  lifecycle: ApplicationLifecycle,
                                  @NamedCache("json-cache") jsonCache: SyncCacheApi,
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
      if (serverInfo.isRight && schemaVersion.isRight) {
        RepositoryState(facadeConfig, serverInfo.toOption.get, schemaVersion.toOption.get)
      } else if (serverInfo.isRight && schemaVersion.isLeft) {
        RepositoryState(facadeConfig, serverInfo.toOption.get)
      } else if (serverInfo.isLeft && schemaVersion.isLeft) {
        RepositoryState(facadeConfig)
      } else {
        RepositoryState(facadeConfig, schemaVersion.toOption.get)
      }
    }

  }

  /**
   * Takes a path and attempts to resolve it to an underlying repository id (in the case of OTCS, this will be a DataID)
   *
   * @return a [[RepositoryResult]] either containing a valid identifier, or an error wrapped within a [[Throwable]]
   */
  override def resolvePath(path: List[String]): Future[RepositoryResult[NodeDetails]] = {
    val nodeId = dbContext.queryNodeDetailsByPath(applyPathExpansions(path.map(s => URLDecoder.decode(s, "UTF-8"))))
    nodeId map {
      case Right(details) => Right(details)
      case Left(t) => Left(t)
    }
  }

  /**
   * Takes a path and then applies any relevant path expansions. Basically, the first element in the path is examined, and if it appears
   * in the currently configured [[FacadeConfig]] path expansions map, it is replaced with the elements in this map
   *
   * @param path the path to apply expansion to
   * @return an expanded path
   */
  private def applyPathExpansions(path: List[String]): List[String] = {
    path match {
      case Nil => path
      case head :: tail =>
        facadeConfig.pathExpansions.get(head) match {
          case Some(expansion) =>
            expansion.split('/').toList ++ tail
          case None =>
            head :: tail
        }
    }
  }

  /**
   * Renders a node into a [[JsObject]] representation
   *
   * @param details the [[NodeDetails]] for the node
   * @return a [[JsObject]] representing the node
   */
  override def renderNodeToJson(details: NodeDetails, depth: Int): Future[RepositoryResult[JsObject]] = {
    log.trace(s"Rendering node [$details, depth=$depth]")
    recursiveRender(details, depth).map {
      Right(_)
    } recover {
      case t => Left(t)
    }
  }

  /**
   * Retrieve the contents of a given node (i.e. document)
   *
   * @param details the [[NodeDetails]] associated with the document
   * @param version the version to retrieve. If set to None, then the latest version will be retrieved
   * @return a [[FileInformation]] instance containing details about the temporary file location for the file
   */
  override def retrieveNodeContent(details: NodeDetails, version: Option[Long]): Future[RepositoryResult[DownloadedContent]] = {
    log.trace(s"Downloading content for $details")
    cwsProxy.downloadNodeVersion(details.core.dataId, version) map {
      case Right(info) =>
        Right(info)
      case Left(t) =>
        Left(t)
    } recover {
      case t =>
        log.error(s"Couldn't retrieve content for $details : '${t.getMessage}")
        Left(t)
    }
  }

  /**
   * Recursively renders a node to Json, returning a [[Future]]. This method is cache aware
   *
   * @param details the [[NodeDetails]] of the node to render
   * @param depth   the depth to depth
   * @return
   */
  private def recursiveRender(details: NodeDetails, depth: Int): Future[JsObject] = {
    log.trace(s"Rendering $details at depth=$depth")
    import facade.db.JsonWriters._

    val rendition= jsonCache.get[JsObject](details.core.dataId.toString) match {
      case Some(json) =>
        log.trace(s"Json cache *hit* for id=${details.core.dataId}")
        json
      case None =>
        log.trace(s"Json cache *miss* for id=${details.core.dataId}")
        val json = Json.toJson(details).asInstanceOf[JsObject]
        jsonCache.set(details.core.dataId.toString, json, facadeConfig.jsonCacheLifetime)
        json
    }

    depth match {
      case 0 => Future.successful(rendition)
      case _ =>
        dbContext.queryChildrenDetails(details) flatMap {
          case Right(l) =>
            Future.sequence(l.filter(d => !d.core.name.startsWith("@[")).map(recursiveRender(_, depth - 1))) map { children =>
              if (children.isEmpty) {
                rendition
              } else {
                rendition ++ Json.obj("children" -> JsArray(children))
              }
            }
          case Left(_) => Future.successful(Json.obj())
        }
    }

  }

  /**
   * Uploads a file to a given location, either adding a new document or adding a version to an existing document. This method will then
   * return a rendition of the new/existing node as a [[JsObject]]
   *
   * @param parentDetails the [[NodeDetails]] associated with the parent node, or the node to add a version to
   * @param meta          a [[JsObject]] containing the KV pairs to be applied as meta-data for the uploaded document
   * @param filename      the original/required filename for the document
   * @param source        the path to a file containing the contents of the document
   * @param size          the size of content to upload
   * @return
   */
  override def uploadContent(parentDetails: NodeDetails, meta: Option[JsObject], filename: String, source: Path, size: Long)
  : Future[RepositoryResult[JsObject]] = {
    cwsProxy.uploadNodeContent(parentDetails.core.dataId, meta, filename, source, size) flatMap {
      case Right(node) =>
        dbContext.queryNodeDetailsById(node.getID) flatMap {
          case Right(details) =>
            renderNodeToJson(details, 0)
          case Left(t) =>
            Future.successful(Left(t))
        }
      case Left(t) =>
        log.error(s"Error occurred during content upload '${t.getMessage}'")
        Future.successful(Left(t))
    }
  }

  /**
   * Updates the meta-data associated with a given [[NodeDetails]] instance
   *
   * @param details the [[NodeDetails]] associated with the node to update
   * @param meta    a [[JsObject]] containing the KV pairs defining the updates to make
   * @return a [[JsObject]] rendition of the updated node
   */
  override def updateNodeMetaData(details: NodeDetails, meta: JsObject): Future[RepositoryResult[JsObject]] = {
    cwsProxy.updateNodeMetaData(details.core.dataId, meta) flatMap {
      case Right(_) =>
        jsonCache.remove(details.core.dataId.toString)
        renderNodeToJson(details, 0)
      case Left(t) =>
        Future.successful(Left(t))
    }
  }
}
