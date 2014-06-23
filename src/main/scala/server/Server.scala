package server

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http
import autocomplete.buc._
import autocomplete.datasource.AOLSearchSource
import spray.can.server.UHttp

object Server extends App {
  val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
  val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
  val buc: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)
  implicit lazy val system = ActorSystem("autocomplete-server")

  val searchServer = system.actorOf(Props(new HttpSearchServer(buc)))
  val socketServer = system.actorOf(Props(new SocketServer(buc)))
  //IO(Http)  ! Http.Bind(searchServer, "localhost", 8080)
  IO(UHttp) ! Http.Bind(socketServer, "localhost", 8080)
}

