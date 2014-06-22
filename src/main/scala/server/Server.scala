package server

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http

object Server extends App {
  implicit lazy val system = ActorSystem("autocomplete-server")
  val searchServer = system.actorOf(Props[HttpSearchServer])
  IO(Http) ! Http.Bind(searchServer, "localhost", port = 8080)
}



