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
  def resolvePath(path : List[String]) : Future[RepositoryResult[NodeCoreDetails]]

  /**
   * Renders a node into a [[JsObject]] representation
   * @param details the [[NodeCoreDetails]] for the node
   * @param depth the depth of the rendition (number of levels of children)
   * @return a [[JsObject]] representing the node
   */
  def renderNodeToJson(details : NodeCoreDetails, depth : Int) : Future[RepositoryResult[JsObject]]

  /**
   * Retrieve the contents of a given node (i.e. document)
   * @param details the [[NodeCoreDetails]] associated with the document
   * @param version the version to retrieve. If set to None, then the latest version will be retrieved
   * @return a [[FileInformation]] instance containing details about the temporary file location for the file
   */
  def retrieveNodeContent(details : NodeCoreDetails, version : Option[Int]) : Future[RepositoryResult[FileInformation]]

}
