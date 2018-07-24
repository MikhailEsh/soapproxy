package run

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import sign.SoapSign

import scala.concurrent.ExecutionContext

object AppContext extends LazyLogging {
  val config: Config = ConfigFactory.defaultApplication()
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val executor: ExecutionContext = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val router = new RootRouter()
  val passfrase = "1qwert"
  val soapSign = new SoapSign(passfrase)
}
