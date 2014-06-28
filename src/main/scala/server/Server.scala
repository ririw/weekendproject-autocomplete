package server

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http
import autocomplete.buc._
import autocomplete.datasource.AOLWordSearchSource
import spray.can.server.UHttp

object Server extends App {
  val searchDataSet: AOLWordSearchSource = AOLWordSearchSource.testingSearches()
  val searches: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSet)
  val buc: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
    new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searches, 3)
  implicit lazy val system = ActorSystem("autocomplete-server")

  def stringToQuery(string: String): WordSearchSourceQuery = WordSearchSourceQuery.makeQuery(string.toLowerCase)
  def queryToString(query: WordSearchSourceQuery): String = query.query.mkString(" ")
  val searchServer = system.actorOf(Props(new HttpSearchServer(buc, stringToQuery, queryToString)))
  val socketServer = system.actorOf(Props(new SocketServer(buc, stringToQuery, queryToString)))
  //IO(Http)  ! Http.Bind(searchServer, "localhost", 8080)
  IO(UHttp) ! Http.Bind(socketServer, "localhost", 8080)
}

