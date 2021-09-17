package facade.repository

import facade._
import facade.cws.CwsProxy
import facade.db.{DbContext, NodeCoreDetails}
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsObject, Json}
import play.api.{Configuration, Logger}

import java.net.URLDecoder
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
                                  @NamedCache("json-cache") cache: AsyncCacheApi,
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

  /**
   * Takes a path and then applies any relevant path expansions. Basically, the first element in the path is examined, and if it appears
   * in the currently configured [[FacadeConfig]] path expansions map, it is replaced with the elements in this map
   * @param path the path to apply expansion to
   * @return an expanded path
   */
  private def applyPathExpansions(path : List[String]) :  List[String] = {
    path match {
      case Nil => path
      case head :: tail => {
          facadeConfig.pathExpansions.get(head) match {
            case Some(expansion) => {
              expansion.split('/').toList  ++ tail
            }
            case None => {
              head :: tail
            }
          }
      }
    }
  }

  /**
   * Takes a path and attempts to resolve it to an underlying repository id (in the case of OTCS, this will be a DataID)
   *
   * @return a [[RepositoryResult]] either containing a valid identifier, or an error wrapped within a [[Throwable]]
   */
  override def resolvePath(path: List[String]): Future[RepositoryResult[NodeCoreDetails]] = {
    val nodeId= dbContext.queryNodeDetailsByPath(applyPathExpansions(path.map(s => URLDecoder.decode(s, "UTF-8"))))
    nodeId map {
      case Right(id) => Right(id)
      case Left(t) => Left(t)
    }
  }

  /**
   * Recursively renders a node to Json, returning a [[Future]]
   * @param acc accumlated [[JsObject]] which will ultimately contain the completed rendition
   * @param id the id of the node to render
   * @param depth the depth to depth
   * @return
   */
  private def renderNode(acc : JsObject, id : Long, depth : Int) : Future[JsObject] = ???

  /**
   * Renders a node into a [[JsObject]] representation
   *
   * @param id the id for the node
   * @return a [[JsObject]] representing the node
   */
  override def renderNode(id: Long, depth : Int): Future[RepositoryResult[JsObject]] = {
    log.trace(s"Rendering node [${id}, depth=${depth}]")
    renderNode(Json.obj(), id, depth) map { rendition =>
      Right(rendition)
    } recover {
      case t => Left(t)
    }
  }
}
