package facade.db

import facade.db.Model._

import scala.concurrent.Future
import scala.util.Either


/**
 * Trait defining the responsibilities for the code managing the underlying database connection
 */
trait DbContext {

  /**
   * Type alias for results
   *
   * @tparam T The type of the successful type.  Convention is that a successful computation places result in the Right.
   */
  type DbContextResult[T] = Either[Throwable, T]

  /**
   * Gets the version of the underlying repository (OTCS) schema and returns it as a string
   *
   * @return the current version of the underlying repository schema
   */
  def schemaVersion(): Future[DbContextResult[String]]

  /**
   * Executes a series of queries in order to retrieve the core details about a node identified by a path relative to a root
   *
   * @param path a list of path elements, which when combined form a complete relative path of the form A/B/C
   * @return a [[Future]] containing a [[DbContextResult]] which can either be a [[NodeDetails]] instance or a [[Throwable]]
   */
  def queryNodeDetailsByPath(path: List[String]): Future[DbContextResult[NodeDetails]]

  /**
   * Returns a list of the child ids for a given node
   *
   * @param details the [[NodeDetails]] for the parent
   * @return a [[Future]] containing a [[DbContextResult]] which can either be a list of node ids or a [[Throwable]]
   */
  def queryChildrenDetails(details: NodeDetails): Future[DbContextResult[List[NodeDetails]]]

  /**
   * Queries for the [[NodeDetails]] of a node given an id
   *
   * @param id the id of the node to search for
   * @return a result hopefully containing a [[NodeDetails]] instance
   */
  def queryNodeDetailsById(id: Long): Future[DbContextResult[NodeDetails]]

  /**
   * Requests that the context clears any cached information relating to the given node identifier
   *
   * @param id a node identifier
   */
  def clearCachedContent(id: Long): Unit

  /**
   * Executes a query which is presented in the defined query grammar defined within  [[QueryParser]]
   *
   * @param query a query defined in terms of the grammar implemented and understood by [[QueryParser]]
   * @return an optional result containing a list of [[NodeDetails]] instances representing hits against the query
   */
  def executeQuery(query: String): Future[DbContextResult[List[NodeDetails]]]
}
