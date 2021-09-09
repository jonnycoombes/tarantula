import com.google.inject.AbstractModule
import facade._
import play.api.{Configuration, Environment, Logger}


/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.

 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module(environment: Environment, configuration : Configuration) extends AbstractModule {

  /**
   * The logger for the [[Module]]
   */
  private val log = Logger(MAIN_LOG)

  /**
   * Create and bind any singleton classes during application startup
   */
  override def configure(): Unit = {
    log.info("Initialising core facade module - instantiating singletons")
    log.debug("Instantiating a repository instance as an eager singleton")
    bind(classOf[facade.repository.DefaultRepository]).asEagerSingleton()
  }

}
