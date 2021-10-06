package facade.repository

import facade.cws.DownloadedContent
import facade.db.Model._
import play.api.libs.json.JsObject

import java.nio.file.Path
import scala.concurrent.Future

/**
 * Trait which should be implemented by the underlying repository.  In the base implementation of the facade this trait is implemented on
 * top of a merged OTCS/SQL API layer.
 */
trait Repository {

  /**
   * Type alias for results
   *
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
   *
   * @return a [[RepositoryResult]] either containing a valid identifier, or an error wrapped within a [[Throwable]]
   */
  def resolvePath(path: List[String]): Future[RepositoryResult[NodeDetails]]

  /**
   * Renders a node into a [[JsObject]] representation
   *
   * @param details the [[NodeCoreDetails]] for the node
   * @param depth   the depth of the rendition (number of levels of children)
   * @return a [[JsObject]] representing the node
   */
  def renderNodeToJson(details: NodeDetails, depth: Int): Future[RepositoryResult[JsObject]]

  /**
   * Retrieve the contents of a given node (i.e. document)
   *
   * @param details the [[NodeCoreDetails]] associated with the document
   * @param version the version to retrieve. If set to None, then the latest version will be retrieved
   * @return a [[DownloadedContent]] instance containing details about the temporary file location for the file
   */
  def retrieveNodeContent(details: NodeDetails, version: Option[Long]): Future[RepositoryResult[DownloadedContent]]

  /**
   * Uploads a file to a given location, either adding a new document or adding a version to an existing document. This method will then
   * return a rendition of the new/existing node as a [[JsObject]]
   *
   * @param parentDetails the [[NodeCoreDetails]] associated with the parent node, or the node to add a version to
   * @param meta          a [[JsObject]] containing the KV pairs to be applied as meta-data for the uploaded document
   * @param filename      the original/required filename for the document
   * @param source        the path to a file containing the contents of the document
   * @param size          the size of content to upload
   * @return
   */
  def uploadContent(parentDetails: NodeDetails, meta: Option[JsObject], filename: String, source: Path, size: Long)
  : Future[RepositoryResult[JsObject]]

  /**
   * Updates the meta-data associated with a given [[NodeCoreDetails]] instance
   *
   * @param details the [[NodeCoreDetails]] associated with the node to update
   * @param meta    a [[JsObject]] containing the KV pairs defining the updates to make
   * @return a [[JsObject]] rendition of the updated
   */
  def updateNodeMetaData(details: NodeDetails, meta: JsObject): Future[RepositoryResult[JsObject]]


  def search(query : String) : Future[RepositoryResult[List[JsObject]]]

}
