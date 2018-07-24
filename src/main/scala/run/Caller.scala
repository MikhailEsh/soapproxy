package run

import java.net.InetSocketAddress

import akka.http.scaladsl.{ClientTransport, Http}
import AppContext._
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import scala.collection.immutable
import scala.concurrent.Future

object Caller extends LazyLogging {

  val config = ConfigFactory.load()

  def proxyHttp(soapMsg: String, headersRq: immutable.Seq[HttpHeader]): Future[HttpResponse] = {
    val request = HttpRequest(
      HttpMethods.POST,
      uri = config.getString("http-proxy.path"),
      headers = headersRq,
      entity = soapMsg
    )
    Http().singleRequest(request)
  }
}
