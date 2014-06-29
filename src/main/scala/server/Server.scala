package server

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import autocomplete.{GlobalReg, LetterTrieAutoCompleter_Production}
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit

object Server extends App {
  val reporter = ConsoleReporter.forRegistry(GlobalReg.reg)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()
  reporter.start(10, TimeUnit.SECONDS)

  LetterTrieAutoCompleter_Production.autoComplete("asd")
  implicit lazy val system = ActorSystem("autocomplete-server")
  val searchServer = system.actorOf(Props(new HttpSearchServer(LetterTrieAutoCompleter_Production)))
  val socketServer = system.actorOf(Props(new SocketServer(LetterTrieAutoCompleter_Production)))
  //IO(Http)  ! Http.Bind(searchServer, "localhost", 8080)
  IO(UHttp) ! Http.Bind(socketServer, "localhost", 8080)
}

