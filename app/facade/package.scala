package object facade {

  /**
   * The main facade log
   */
  lazy val MAIN_LOG = "facade"

  /**
   * A dedicated log for [[facade.repository.Repository]] implementations
   */
  lazy val REPOSITORY_LOG = "repository"

}
