package server

import akka.actor.{ActorLogging, Actor}
import spray.can.Http
import spray.http._
import MediaTypes._
import spray.http._
import spray.http.HttpHeaders.{`Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`, Connection}
import autocomplete.buc.{SearchSourceQuery, BucComputation, SearchSourceDataSet}
import spray.json._
import DefaultJsonProtocol._
import spray.http.HttpRequest
import spray.http.HttpResponse
import scala.Some

class HttpSearchServer(buc: BucComputation[SearchSourceQuery, SearchSourceDataSet]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case msg@HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      msg.uri.query.get("q") match {
        case None => HttpResponse(StatusCodes.BadRequest, pathPage, headers=closeConn)
        case Some(query) =>
          val q = SearchSourceQuery(query.toLowerCase)
          log.info("Got: " + q.toString)
          val result = buc.getRefinements(q).getOrElse(List().toIterator).toList.sortBy(-_._2)
          val suggested = result.take(5).toList.map(_._1)
          val suggestionStrings = suggested.map(_.query.mkString(" "))
          val results: Map[String, JsValue] = Map("results" -> JsArray(suggestionStrings.map(JsString(_))), "query" -> JsString(query))
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
