package server

import akka.actor._
import akka.actor.Props
import spray.can.websocket
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.routing.HttpServiceActor
import akka.actor.{ActorRef, ActorLogging, Actor}
import spray.can.Http
import spray.http._
import MediaTypes._
import spray.http._
import autocomplete.buc.{SearchSourceQuery, BucComputation, SearchSourceDataSet}
import spray.json._
import DefaultJsonProtocol._

import spray.http.HttpHeaders.{Connection, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`}
import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import spray.can.websocket.FrameCommandFailed


class SocketServer(buc: BucComputation[SearchSourceQuery, SearchSourceDataSet]) extends Actor with ActorLogging {
  def receive = {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      val serverConnection = sender()
      val conn = context.actorOf(WebSocketWorker.props(serverConnection, buc))
      serverConnection ! Http.Register(conn)
  }
}
final case class Push(msg: String)
object WebSocketWorker {
  def props(serverConnection: ActorRef, buc: BucComputation[SearchSourceQuery, SearchSourceDataSet]) =
    Props(classOf[WebSocketWorker], serverConnection, buc)
}
class WebSocketWorker(val serverConnection: ActorRef, buc: BucComputation[SearchSourceQuery, SearchSourceDataSet])
  extends HttpServiceActor with websocket.WebSocketServerConnection {
  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogic: Receive = {
    // just bounce frames back for Autobahn testsuite
    case x @ (_: BinaryFrame | _: TextFrame) =>
      val xString = x match {
        case bf: BinaryFrame => BinaryFrame.unapply(bf).map(_.decodeString("UTF-8"))
        case tf: TextFrame => TextFrame.unapply(tf).map(_.decodeString("UTF-8"))
      }
      sender() ! xString.map { query =>
        val queryWords = SearchSourceQuery(query.split(' ').map(_.toLowerCase) toList)
        val results = getQueryResults(queryWords)
        sender ! TextFrame(results.toJson.compactPrint)
      }
    case x: FrameCommandFailed =>
      log.error("frame command failed", x)
    case x: HttpRequest => println(x)
  }

  def businessLogicNoUpgrade: Receive = {
    case _: Http.Connected => sender ! Http.Register(self)
    case msg@HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      msg.uri.query.get("q") match {
        case None => HttpResponse(StatusCodes.BadRequest, pathPage, headers=closeConn)
        case Some(query) =>
          val queryWords = SearchSourceQuery(query.split(' ').map(_.toLowerCase) toList)
          val results = getQueryResults(queryWords)
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
  
  private def getQueryResults(queryWords: SearchSourceQuery): Map[String, List[String]] = {
    log.info("Got: " + queryWords.toString)
    val result = buc.getRefinements(queryWords).getOrElse(List().toIterator).toList.sortBy(-_._2)
    val suggested = result.take(5).toList.map(_._1)
    val suggestionStrings = suggested.map(_.query.mkString(" "))
    Map("results" -> suggestionStrings, "query" -> queryWords.query)
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