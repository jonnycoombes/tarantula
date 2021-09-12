package facade.cws

/**
 * Trait defining the interface for a [[CwsProxy]] proxy responsible for making Cws out-calls, [[com.opentext.OTAuthentication]]
 * token management and service instantiation. Note that implementors of this trait will only need to implement a subset of the overall
 * Cws surface area - in particular, the surface area required by the facade. Implementors of this trait can then be injected into
 * controllers, services which need to interact (formally) with an underlying CWS instance.
 */
trait CwsProxy {


}
