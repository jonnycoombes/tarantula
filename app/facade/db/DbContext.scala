package facade.db

import scala.concurrent.Future

/**
 * Trait defining the responsibilities for the code managing the underlying database connection
 */
trait DbContext {

  /**
   * Gets the version of the underlying repository (OTCS) schema and returns it as a string
   * @return the current version of the underlying repository schema
   */
  def schemaVersion() : Future[String]

}
