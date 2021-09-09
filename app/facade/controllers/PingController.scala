package facade.controllers

import akka.actor.ActorSystem
import facade.LogNames
import play.api.libs.json.JsString
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}

@Singleton
class PingController @Inject()(val cc : ControllerComponents,
                               val system : ActorSystem,
                               val config : Configuration) extends AbstractController(cc){

  /**
   * The logger for this controller
   */
  lazy val log: Logger = Logger(LogNames.MainLogger)

  /**
   * Generates a ping response, which may be used to check the health of the facade service
   * @return
   */
  def ping() : Action[AnyContent] = Action {
    Ok(ResponseHelpers.success(JsString("ping!")))
  }

}
