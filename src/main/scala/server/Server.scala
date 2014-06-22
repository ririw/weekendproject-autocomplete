package server

import akka.actor.{ActorLogging, Props, Actor, ActorSystem}
import akka.io.IO
import spray.can.Http
import spray.http._
import MediaTypes._
import spray.http._
import spray.http.HttpHeaders.{`Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`, Connection}
import autocomplete.datasource.AOLSearchSource
import autocomplete.buc.{SearchSourceQuery, BucComputation, SearchSourceDataSet}
import spray.json._
import DefaultJsonProtocol._

object Server extends App {
  implicit lazy val system = ActorSystem("autocomplete-server")
  val searchServer = system.actorOf(Props[HttpSearchServer])
  IO(Http) ! Http.Bind(searchServer, "localhost", port = 8080)
}

class HttpSearchServer() extends Actor with ActorLogging {
  val searchDataSet: AOLSearchSource = AOLSearchSource.productionSearches()
  val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
  val buc: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)

  override def receive: Receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case msg@HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      msg.uri.query.get("q") match {
        case None => HttpResponse(StatusCodes.BadRequest, pathPage, headers=closeConn)
        case Some(query) =>
          val q = SearchSourceQuery(query.split(' ').map(_.toLowerCase) toList)
          log.info("Got: " + q.toString)
          val result = buc.getRefinements(q).getOrElse(List().toIterator).toList.sortBy(-_._2)
          val suggested = result.take(10).toList.map(_._1)
          val suggestionStrings = suggested.map(_.query.mkString(" "))
          val results = Map("results" -> suggestionStrings, "query" -> q.query)
          sender ! HttpResponse(entity=HttpEntity(`application/json`, results.toJson.compactPrint), headers=`Access-Control-Allow-Origin`(AllOrigins) :: closeConn)
      }
    case HttpRequest(HttpMethods.OPTIONS, path, _, _, _) =>
      sender ! HttpResponse(headers=List(
        `Access-Control-Allow-Origin`(AllOrigins),
        `Access-Control-Allow-Methods`(HttpMethods.GET))
      )
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

