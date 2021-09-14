package facade.db

import scala.concurrent.Future
import scala.util.Either



/**
 * Trait defining the responsibilities for the code managing the underlying database connection
 */
trait DbContext {

  /**
   * Type alias for results
   * @tparam T The type of the successful type.  Convention is that a successful computation places result in the Right.
   */
  type DbContextResult[T] = Either[Throwable, T]

  /**
   * Gets the version of the underlying repository (OTCS) schema and returns it as a string
   * @return the current version of the underlying repository schema
   */
  def schemaVersion() : Future[DbContextResult[String]]

  /**
   * Executes a query (or series of queries) in order to retrieve the core details about a given node
   * @param parentId an optional parentId for the node. If None, then the id is taken to be top-level
   * @param name the name of the node to look for
   * @return a [[Future]] containing a [[DbContextResult]]
   */
  def queryNodeDetailsByName(parentId : Option[Long], name : String) : Future[DbContextResult[Long]]

}
