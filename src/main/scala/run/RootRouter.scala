package run

import java.net.URL

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class RootRouter(implicit executionContext: ExecutionContext) extends LazyLogging {

  val routes: Route = handleExceptions(exceptionHandler) {
    extractUri { uri =>
      extractMethod { method =>
        logger.info("{} {}", method.value, uri.toRelative.path)
        ignoreTrailingSlash {
          path("proxy") {
            post {
              extractRequest { entity =>
                onComplete(Caller.proxyHttp(entity.entity, entity.headers)) {
                  case Success(result) => complete(result)
                  case Failure(exception) =>
                    logger.error("Something broke during send request", exception)
                    complete(StatusCodes.InternalServerError, exception)
                }
              }
            }
          }
        }
      }
    }
  }

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Exception =>
      logger.error("Exception: ", e)
      complete(StatusCodes.InternalServerError, e)
  }
}
