package run

import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import AppContext._
import scala.util.{Failure, Success}

object Main extends App with LazyLogging {
    try {
      val config = ConfigFactory.load()
      Http().bindAndHandle(router.routes, config.getString("http.host"), config.getInt("http.port")).onComplete {
        case Success(b) => logger.info("Bound on {}", b.localAddress)
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