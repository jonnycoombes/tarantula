package facade.cws

import com.opentext.cws.admin.ServerInfo
import com.opentext.cws.authentication.OTAuthentication
import com.opentext.cws.docman.Node
import play.api.libs.json.JsObject

import java.nio.file.Path
import scala.concurrent.Future

/**
 * Trait defining the interface for a [[CwsProxy]] proxy responsible for making Cws out-calls, [[com.opentext.OTAuthentication]]
 * token management and service instantiation. Note that implementors of this trait will only need to implement a subset of the overall
 * Cws surface area - in particular, the surface area required by the facade. Implementors of this trait can then be injected into
 * controllers, services which need to interact (formally) with an underlying CWS instance.
 */
trait CwsProxy {

  /**
   * Type alias for results
   *
   * @tparam T the expected success type. Convention is that a successful computation places result in the Right.
   */
  type CwsProxyResult[T] = Either[Throwable, T]

  /**
   * Attempts an authentication and returns the resultant [[OTAuthentication]] structure containing the token
   *
   * @return an [[OTAuthentication]] structure containing the authentication token, otherwise an exception
   */
  def authenticate(): Future[CwsProxyResult[OTAuthentication]]

  /**
   * Async wrapped call to [[com.opentext.cws.admin.AdminService]] GetServerInfo
   *
   * @return
   */
  def serverInfo(): Future[CwsProxyResult[ServerInfo]]

  /**
   * Retrieve a node based on it's id
   *
   * @param id the id of the node
   * @return a [[Future]] wrapping a [[CwsProxyResult]]
   */
  def nodeById(id: Long): Future[CwsProxyResult[Node]]

  /**
   * Attempts to retrieve the content associated with a given node version
   *
   * @param id      the id for the node
   * @param version the version to download.  If *None*, the latest version will be downloaded
   * @return A [[DownloadedContent]] instance containing the contents along with length and content type information
   */
  def downloadNodeVersion(id: Long, version: Option[Long]): Future[CwsProxyResult[DownloadedContent]];

  /**
   * Uploads new content to a given parent node (either a folder or a document as a new version) and returns a new [[Node]]
   *
   * @param parentId the parent id of the node
   * @param meta     a optional [[JsObject]] containing the meta-data to be applied to the node
   * @param filename the filename to apply to the new content
   * @param source   a file containing the the content of the file to upload
   * @param size     the size of the content to upload
   * @return
   */
  def uploadNodeContent(parentId: Long, meta: Option[JsObject], filename: String, source: Path, size: Long): Future[CwsProxyResult[Node]]

}
