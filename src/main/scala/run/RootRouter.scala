package run

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import scala.concurrent.ExecutionContext

class RootRouter(implicit executionContext: ExecutionContext) extends LazyLogging {

  val routes: Route = handleExceptions(exceptionHandler){
    extractUri { uri =>
      extractMethod { method =>
        logger.debug("{} {}", method.value, uri.toRelative.path)
        ignoreTrailingSlash {
          proxyRoutes
        }
      }
    }
  }

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RepositoryError => complete(e.httpCode, e.response)
    case e: Exception =>
      logger.error("Exception: ", e)
      complete(StatusCodes.InternalServerError)
  }

  private def proxyRoutes: Route = path("proxy") {
     post {
       extractRequestEntity { entity =>
         val a = entity.httpEntity
//         println(a.getDataBytes())
         complete(s"Request entity content-type is ${entity.contentType}")
       }
     }
  }
}
