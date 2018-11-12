package csw.auth

import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Success, Try}

object Example extends App {

  val accessToken: Try[AccessToken] = AccessToken.decode(
    "header.payload.signature",
    ConfigFactory.load()
  )

  accessToken match {
    case Failure(exception) =>
      System.err.println(exception.getMessage)
      sys.exit(1)
    case Success(at) =>
      println(s"access token: $at")
  }
}