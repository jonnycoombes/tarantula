package facade.filters

import akka.stream.Materializer
import facade.LogNames
import play.api.Logger
import play.api.mvc.{Filter, RequestHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 *
 * @param mat
 * @param ec
 */
class TimingFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  lazy val log: Logger = Logger(LogNames.TimingsLogger)

  override def apply(f: RequestHeader => Future[Result])(header: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis()
    f(header).map { result =>
      val elapsed = System.currentTimeMillis() - startTime
      log.trace(s"[${header.id}, ${header.remoteAddress}, ${header.method}, ${header.path}, ${elapsed.toString}]")
      result.withHeaders(FacadeHeaders.TimingsHeader -> elapsed.toString)
    }

  }
}
