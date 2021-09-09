package facade

/**
 * Contains any cross-cutting/global stuff relating to custom filter implementations
 */
package object filters {

  object FacadeHeaders {

    /**
     * The header used to return per-request timing information
     */
    lazy val TimingsHeader = "X-FACADE-TIMING-MS"

  }

}
