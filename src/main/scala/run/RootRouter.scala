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
                  s"\n Proxy target: ${loader.hostTarget}" +
                  s"\n Debug Logger: ${loader.getDebug()}")
              } ~ post {
              extractRequest { entity =>
                if (loader.getDebug()) {
                  logger.info(s"Data sign parameter \n" +
                    s"\n host: ${loader.host}:${loader.port}" +
                    s"\n passfrase: ${loader.passfrase}" +
                    s"\n Exist File: ${loader.ksFile.exists()}" +
                    s"\n alias: ${loader.alias}"
                  )
                  logger.info(s"Request succeed get \n method: ${entity.method}" +
                    s"\n uri: ${entity.uri}" +
                    s"\n headers: ${entity.headers}" +
                    s"\n body: ${entity.entity}"
                  )
                }
                onComplete(callProxy(entity)) {
                  case Success(result) =>
                    logger.info("Response successed received ")
                    if (loader.getDebug()) {
                      logger.info(s"Body response: $result")
                    }
                    complete(result)
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
            } ~ put {
              loader.turnDebug()
              complete(StatusCodes.OK,
                s"  Debug Logger: ${loader.getDebug()}")
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
    if (loader.getDebug()) {
      logger.info(s"Message parameter \n method: ${request.method}" +
        s"\n uri: ${request.uri}" +
        s"\n headers: ${request.headers}" +
        s"\n body: ${request.entity}"
      )
    }
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
        if (loader.getDebug()) {
          logger.info(s"Message prepare for sign $f")
        }
        logger.info(s"Message success extracted")
        val singned = soapSign.signSoapMessage(f)
        if (loader.getDebug()) {
          logger.info(s"Message success signed body: $singned")
        }
        logger.info(s"Message success signed")
        singned
      })
  }

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: Exception =>
      logger.error("Exception: ", e)
      complete(StatusCodes.InternalServerError, e)
  }
}
