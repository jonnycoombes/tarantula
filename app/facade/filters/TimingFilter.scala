package facade.filters

import akka.stream.Materializer
import facade.LogNames
import play.api.Logger
import play.api.mvc.{Filter, RequestHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * A simple filter that computes the server-side computation time (in ms) and then injects it into a custom header.  Useful telemetry for
 * benchmarking and performance testing
 * @param mat a [[akka.stream.Materializer]] instance, which could be used for stream manipulation
 * @param ec an [[ExecutionContext]] used to performing which can be used to perform any computations OOB
 */
class TimingFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  lazy val log: Logger = Logger(LogNames.TimingsLogger)

  override def apply(f: RequestHeader => Future[Result])(header: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis()
    f(header).map { result =>
      val elapsed = System.currentTimeMillis() - startTime
      log.trace(s"[${header.id}, ${header.remoteAddress}, ${header.method}, ${header.path}, ${header.rawQueryString}, ${elapsed.toString}]")
      result.withHeaders(FacadeHeaders.TimingsHeader -> elapsed.toString)
    }

  }
}
