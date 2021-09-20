package facade

import akka.actor.ActorSystem
import facade.repository.Repository
import play.api.libs.concurrent.CustomExecutionContext

import javax.inject.{Inject, Singleton}

/**
 * Any statics/helpers relating to WCS can go in here...
 */
package object cws {
  /**
   * A [[CustomExecutionContext]] for use by [[CwsProxy]] implementations, so that operations are performed within their own thread
   * pool
   * @param system an injected [[ActorSystem]]
   */
  @Singleton
  class CwsProxyExecutionContext @Inject()(system : ActorSystem) extends CustomExecutionContext(system, "facade.cws.dispatcher")

  /**
   * Returned by download methods (e.g. content downloads)
   * @param file a temporary file location
   * @param length the length of the file
   * @param contentType the content/MIME type of the file
   */
  case class DownloadedContent(file : java.io.File, length : Long, contentType : String)

}
