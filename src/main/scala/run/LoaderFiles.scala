package run

import java.io.{File, FileNotFoundException}

import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}

case class LoaderFiles () {

  private var isDebugLog = false

  def turnDebug(): Unit = {
    this.synchronized {
      isDebugLog = !isDebugLog
    }
  }

  def getDebug(): Boolean = isDebugLog

  private val config: Config = {
    ConfigFactory.defaultApplication()
    val myConfigFile = new File(System.getProperty("user.dir") + File.separator + "application.conf")
    if (!myConfigFile.exists || myConfigFile.isDirectory) throw new FileNotFoundException("File application.conf Not Found")
    ConfigFactory.parseFileAnySyntax(myConfigFile, ConfigParseOptions.defaults)
  }
  val passfrase: String = config.getString("http-host.passfrase")
  val alias: String = config.getString("http-host.alias")
  val signatureValidityTime: Int = config.getInt("http-host.timeLive")
  val host: String = config.getString("http-host.host")
  val port: Int = config.getInt("http-host.port")
  val hostTarget: String = config.getString("http-target.host")
  val ksFile: File = {
    val file = new File(System.getProperty("user.dir") + File.separator + "client-keystore.jks")
    if (!file.exists || file.isDirectory) throw new FileNotFoundException("File client-keystore.jks Not Found")
    file
  }
}