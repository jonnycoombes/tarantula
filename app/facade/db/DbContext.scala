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
   * Executes a series of queries in order to retrieve the core details about a node identified by a path relative to a root
   * @param path a list of path elements, which when combined form a complete relative path of the form A/B/C
   * @return a [[Future]] containing a [[DbContextResult]] which can either be a [[NodeCoreDetails]] instance or a [[Throwable]]
   */
  def queryDetailsByPath(path : List[String]) : Future[DbContextResult[NodeCoreDetails]]

  /**
   * Returns a list of the child ids for a given node
   * @param details the [[NodeCoreDetails]] for the parent
   * @return a [[Future]] containing a [[DbContextResult]] which can either be a list of node ids or a [[Throwable]]
   */
  def queryChildrenDetails(details : NodeCoreDetails) : Future[DbContextResult[List[NodeCoreDetails]]]

  /**
   * Queries for the [[NodeCoreDetails]] of a node given an id
   * @param id the id of the node to search for
   * @return a result hopefully containing a [[NodeCoreDetails]] instance
   */
  def queryDetailsById(id : Long) : Future[DbContextResult[NodeCoreDetails]]

}
