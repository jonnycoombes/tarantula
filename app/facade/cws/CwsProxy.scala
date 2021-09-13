package facade.cws

import com.opentext.OTAuthentication

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
   * @tparam T the expected success type. Convention is that a successful computation places result in the Right.
   */
  type CwsProxyResult[T] = Either[Throwable, T]

  /**
   * Attempts an authentication and returns the resultant [[OTAuthentication]] structure containing the token
   * @return an [[OTAuthentication]] structure containing the authentication token, otherwise an exception
   */
  def authenticate() : Future[CwsProxyResult[OTAuthentication]]

}
