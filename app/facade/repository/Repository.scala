package facade.repository

import scala.concurrent.Future

/**
 * Trait which should be implemented by the underlying repository.  In the base implementation of the facade this trait is implemented on
 * top of a merged OTCS/SQL API layer.
 */
trait Repository {
  /**
   * Gets the current repository state information
   *
   * @return an instance of [[RepositoryState]]
   */
  def repositoryState(): Future[RepositoryState]
}
