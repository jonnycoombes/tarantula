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
}
