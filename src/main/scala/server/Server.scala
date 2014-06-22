package server

import akka.actor.{Props, Actor, ActorSystem}
import akka.io.IO
import spray.can.Http
import spray.http._
import MediaTypes._
import spray.http._
import spray.http.HttpHeaders.Connection
import autocomplete.datasource.AOLSearchSource
import autocomplete.buc.{SearchSourceQuery, BucComputation, SearchSourceDataSet}
import spray.json._
import DefaultJsonProtocol._

object Server extends App {
  implicit lazy val system = ActorSystem("autocomplete-server")
  val searchServer = system.actorOf(Props[HttpSearchServer])
  IO(Http) ! Http.Bind(searchServer, "localhost", port = 8080)
}

class HttpSearchServer() extends Actor {
  val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
  val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
  val buc: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)

  override def receive: Receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case msg@HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      msg.uri.query.get("q") match {
        case None => HttpResponse(StatusCodes.BadRequest, pathPage, headers=closeConn)
        case Some(query) =>
          val result = buc.getRefinements(SearchSourceQuery(query.split("w+").map(_.toLowerCase)toList)).getOrElse(List().toIterator)
          val suggested = result.take(10).toList.map(_._1)
          val suggestionStrings = suggested.map(_.query.mkString(" "))
          val results = Map("results" -> suggestionStrings)
          sender ! HttpResponse(entity=HttpEntity(`application/json`, results.toJson.compactPrint), headers=closeConn)
      }
    case msg =>
      println(msg)
      sender ! HttpResponse(StatusCodes.NotFound, pathPage, headers=closeConn)
  }
  val closeConn = List(Connection.apply("close"))

  lazy val pathPage = HttpEntity(`text/html`,
    <html>
      <body>
        <h1>Say hello to <i>spray-can</i>!</h1>
        <p>Defined resources:</p>
        <ul>
          <li><a href="/search?q=XXX">/search?q=XXX</a></li>
        </ul>
      </body>
    </html>.toString())
  lazy val index = HttpResponse(entity = pathPage)
}

