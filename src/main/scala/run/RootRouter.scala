package run

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.{ActorMaterializer, StreamTcpException}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import AppContext._
import akka.http.scaladsl.Http
import sign.SoapSign

import scala.collection.immutable

class RootRouter(soapSign :SoapSign, loader :LoaderFiles) extends LazyLogging {

  val routes: Route = handleExceptions(exceptionHandler) {
    extractUri { uri =>
      extractMethod { method =>
        logger.info("{} {}", method.value, uri.toRelative.path)
        ignoreTrailingSlash {
          path("proxy") {
            get {
              complete(StatusCodes.OK,
                s"  Proxy host: ${loader.host}:${loader.port} " +
                  s"\n Proxy target: ${loader.hostTarget}")
              } ~ post {
              extractRequest { entity =>
                onComplete(callProxy(entity)) {
                  case Success(result) => complete(result)
                  case Failure(exception) =>
                    logger.error("Something broke", exception)
                    exception match {
                      case ex:StreamTcpException =>
                        complete(StatusCodes.BadGateway, ex.getMessage)
                      case ex =>
                        complete(StatusCodes.InternalServerError, exception)
                    }


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
      proxyHttp(soapMsg, entity.headers)
    })
  }

  private def proxyHttp(soapMsg: String, headersRq: immutable.Seq[HttpHeader]): Future[HttpResponse] = {
    val request = HttpRequest(
      HttpMethods.POST,
      uri = loader.hostTarget,
      headers = headersRq,
      entity = soapMsg
    )
    Http().singleRequest(request)
  }

  private def extractSignStr(entity : HttpRequest): Future[String] ={
    val byteSource = entity.entity.dataBytes
    byteSource
      .runFold(ByteString.empty) {
        case (acc, b) => acc ++ b
      }
      .map(s => s.utf8String)
      .map(f => {
        logger.debug(s"Message for sign $f")
        val singned = soapSign.signSoapMessage(f)
        logger.debug(s"Message success signed $f")
        singned
      })
  }

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Exception =>
      logger.error("Exception: ", e)
      complete(StatusCodes.InternalServerError, e)
  }
}
