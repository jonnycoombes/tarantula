package facade.repository

import facade.db.NodeCoreDetails
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 * Trait which should be implemented by the underlying repository.  In the base implementation of the facade this trait is implemented on
 * top of a merged OTCS/SQL API layer.
 */
trait Repository {

  /**
   * Type alias for results
   * @tparam T the expected success type. Convention is that a successful computation places result in the Right.
   */
  type RepositoryResult[T] = Either[Throwable, T]

  /**
   * Gets the current repository state information
   *
   * @return an instance of [[RepositoryState]]
   */
  def repositoryState(): Future[RepositoryState]

  /**
   * Takes a path and attempts to resolve it to an underlying repository id (in the case of OTCS, this will be a DataID)
   * @return a [[RepositoryResult]] either containing a valid identifier, or an error wrapped within a [[Throwable]]
   */
  def resolvePath(path : RepositoryPath) : Future[RepositoryResult[NodeCoreDetails]]

  /**
   * Renders a node into a [[JsObject]] representation
   * @param details the [[NodeCoreDetails]] for the node
   * @param depth the depth of the rendition (number of levels of children)
   * @return a [[JsObject]] representing the node
   */
  def renderNode(details : NodeCoreDetails, depth : Int) : Future[RepositoryResult[JsObject]]

}
