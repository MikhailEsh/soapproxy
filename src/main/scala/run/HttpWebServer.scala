package run

import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import AppContext._
import sign.SoapSign

import scala.util.{Failure, Success}

object HttpWebServer extends LazyLogging {

  def main(args: Array[String]) {
    try {
      val loader = LoaderFiles()
      logger.info(s"Files success loaded ${loader.toString}")
      val soapSign = new SoapSign(loader.passfrase, loader.alias, loader.signatureValidityTime, loader.ksFile)
      val router = new RootRouter(soapSign, loader)
      Http().bindAndHandle(router.routes, loader.host, loader.port).onComplete {
        case Success(b) =>
          logger.info(s"Bound on ${b.localAddress} with proxy host:${loader.host}:${loader.port}" +
            s" proxy-target ${loader.hostTarget}")
        case Failure(e) => throw e
      }
    } catch {
      case e: Throwable =>
        logger.error("Error while initializing app, shutdown", e)
        actorSystem.terminate().onComplete {
          case Success(t) => logger.info("Terminated {}", t)
          case Failure(err) =>
            logger.error("Termination failed with error", err)
            sys.exit(-1)
        }
    }
  }
}