package run

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RootRouter(implicit executionContext: ExecutionContext, materializer: ActorMaterializer) extends LazyLogging {

  val routes: Route = handleExceptions(exceptionHandler) {
    extractUri { uri =>
      extractMethod { method =>
        logger.info("{} {}", method.value, uri.toRelative.path)
        ignoreTrailingSlash {
          path("proxy") {
            post {
              extractRequest { entity =>
                onComplete(callProxy(entity)) {
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

  private def callProxy(entity : HttpRequest): Future[HttpResponse] ={
    extractSignStr(entity).flatMap(soapMsg => {
      Caller.proxyHttp(soapMsg, entity.headers)
    })
  }

  private def extractSignStr(entity : HttpRequest): Future[String] ={
    val byteSource = entity.entity.dataBytes
    byteSource
      .runFold(ByteString.empty) {
        case (acc, b) => acc ++ b
      }
      .map(s => s.utf8String)
      .map(f => {
        AppContext.soapSign.signSoapMessage(f)
      })
  }

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Exception =>
      logger.error("Exception: ", e)
      complete(StatusCodes.InternalServerError, e)
  }
}
