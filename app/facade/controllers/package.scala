package facade

import play.api.libs.json.{JsObject, JsValue, Json}

/**
 * Top level package object for the controllers package.  Statics, helpers etc...go here
 */
package object controllers {

  /**
   * Contains static methods for generating JSON responses in a standard format compliant with previous versions of the facade
   */
  object ResponseHelpers{

    /**
     * The standard success value
     */
    lazy val JsonSuccess: JsObject = Json.obj("ok" -> true)

    /**
     * The standard failure value
     */
    lazy val JsonFailure: JsObject = Json.obj("ok" -> false)

    /**
     * The standard exception value
     */
    lazy val JsonException : JsObject =Json.obj("ok" -> false, "exception" -> true)

    /**
     * Wraps a given response in a success
     * @param response a [[play.api.libs.json.JsValue]] containing the payload for the response
     * @return a response [[play.api.libs.json.JsObject]]
     */
    def success(response : JsValue) : JsObject = {
      JsonSuccess + ("response" -> response)
    }

    /**
     * Overloaded success function which takes a [[play.api.libs.json.JsObject]]
     * @param response A valid [[play.api.libs.json.JsObject]] instance
     * @return a wrapped success object
     */
    def success(response : JsObject) : JsObject = {
      JsonSuccess ++ response
    }

    /**
     * Wraps a given response in a failure
     * @param failure a [[play.api.libs.json.JsValue]] containing the payload for the response
     * @return a response [[play.api.libs.json.JsObject]]
     */
    def failure(failure : JsValue) : JsObject = {
      JsonFailure + ("response" -> failure)
    }

    /**
     * Overloaded failure method which takes a [[[play.api.libs.json.JsObject]]
     * @param failure a valid [[play.api.libs.json.JsObject]]
     * @return a wrapped failure response
     */
    def failure(failure : JsObject) : JsObject = {
      JsonFailure ++ failure
    }

    /**
     * Wraps a given exception in an exception response
     * @param exception a [[play.api.libs.json.JsValue]] containing the payload for the response
     * @return a response [[play.api.libs.json.JsObject]]
     */
    def exceptionFailure(exception : JsValue) : JsObject ={
      JsonException + ("response" -> exception)
    }

    /**
     * Takes an instance of [[Exception]] and then generates a Json response containing the deets
     * @param exception the exception to serialise into the response
     * @return a response [[play.api.libs.json.JsObject]]
     */
    def exceptionFailure(exception : Exception) : JsObject = {
      JsonException ++ Json.obj(
        "message" -> exception.getMessage,
        "cause" -> exception.getCause.getMessage,
        "type" -> exception.getClass.toString
      )
    }

    /**
     * Takes an instance of [[Throwable]] and then generates a Json response containing the deets
     * @param throwable the [[Throwable]] to serialise
     * @return a response [[JsObject]]
     */
    def throwableFailure(throwable: Throwable) : JsObject = {
      val payload = Json.obj("message"->throwable.getMessage, "source" -> throwable.getClass.getSimpleName)
      JsonException ++ payload
    }

  }

}
