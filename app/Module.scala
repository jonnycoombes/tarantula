import com.google.inject.AbstractModule
import facade._
import facade.cws.{CwsProxy, CwsProxyExecutionContext, DefaultCwsProxy}
import facade.db.{DbExecutionContext, SqlServerDbContext}
import facade.repository.{DefaultRepository, RepositoryExecutionContext}
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
  private val log = Logger(LogNames.ControllerLogger)

  /**
   * Create and bind any singleton classes during application startup
   */
  override def configure(): Unit = {
    log.info("Initialising core facade module - instantiating singletons")

    log.info("Binding custom execution contexts")
    bind(classOf[DbExecutionContext]).asEagerSingleton()
    bind(classOf[RepositoryExecutionContext]).asEagerSingleton()
    bind(classOf[CwsProxyExecutionContext]).asEagerSingleton()

    log.info("Binding DbContext as eager singleton")
    bind(classOf[facade.db.DbContext]).to(classOf[SqlServerDbContext]).asEagerSingleton()
    log.debug("Binding Repository as eager singleton")
    bind(classOf[facade.repository.Repository]).to(classOf[DefaultRepository]).asEagerSingleton()
    log.debug("Binding CWS proxy as eager singleton")
    bind(classOf[facade.cws.CwsProxy]).to(classOf[DefaultCwsProxy]).asEagerSingleton()

  }

}
