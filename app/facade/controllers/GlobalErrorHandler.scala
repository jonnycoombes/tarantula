package facade.controllers

import play.api.http.HttpErrorHandler
import play.api.mvc.Results.{Status, Ok}
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class GlobalErrorHandler extends HttpErrorHandler{

  /**
   * Don't really do much with client errors at the moment
   * @param request
   * @param statusCode
   * @param message
   * @return
   */
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(Status(statusCode)(message))
  }

  /**
   * Map [[Throwable]] instances to a valid Json response
   * @param request the [[RequestHeader]] object relating to the request
   * @param exception the [[Throwable]]
   * @return a mapped json response containing details of the exception
   */
  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    Future.successful(
      Ok(ResponseHelpers.throwableFailure(exception))
    )
  }
}
