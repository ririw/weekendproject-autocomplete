package server

import akka.actor._
import akka.actor.Props
import spray.can.websocket
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.routing.{Directives, HttpServiceActor}
import akka.actor.{ActorRef, ActorLogging, Actor}
import spray.can.Http
import spray.http._
import MediaTypes._
import spray.http._
import spray.json._
import DefaultJsonProtocol._

import spray.http.HttpHeaders.{Connection, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`}
import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import spray.can.websocket.FrameCommandFailed
import autocomplete.AutoCompleter


class SocketServer(autoCompleter: AutoCompleter) extends Actor with ActorLogging {
  def receive = {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      val serverConnection = sender()
      val conn = context.actorOf(WebSocketWorker.props(serverConnection, autoCompleter))
      serverConnection ! Http.Register(conn)
  }
}
final case class Push(msg: String)
object WebSocketWorker {
  def props(serverConnection: ActorRef, autoCompleter: AutoCompleter) =
    Props(classOf[WebSocketWorker], serverConnection, autoCompleter)
}
class WebSocketWorker(val serverConnection: ActorRef, autoCompleter: AutoCompleter)
  extends HttpServiceActor with websocket.WebSocketServerConnection with StaticRoutes {
  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogic: Receive = {
    // just bounce frames back for Autobahn testsuite
    case x @ (_: BinaryFrame | _: TextFrame) =>
      val xString = x match {
        case bf: BinaryFrame => BinaryFrame.unapply(bf).map(_.decodeString("UTF-8"))
        case tf: TextFrame => TextFrame.unapply(tf).map(_.decodeString("UTF-8"))
      }
      sender() ! xString.map { query =>
        val results = getQueryResults(query)
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
          val results = getQueryResults(query)
          sender ! HttpResponse(entity=HttpEntity(`application/json`, results.toJson.compactPrint), headers=`Access-Control-Allow-Origin`(AllOrigins) :: closeConn)
      }
    case HttpRequest(HttpMethods.OPTIONS, path, _, _, _) =>
      sender ! HttpResponse(headers=List(
        `Access-Control-Allow-Origin`(AllOrigins),
        `Access-Control-Allow-Methods`(HttpMethods.GET))
      )
    //case req =>
    //  runRoute(staticRoute)
    case msg =>
      println(msg)
      sender ! HttpResponse(StatusCodes.NotFound, pathPage, headers=closeConn)
  }
  
  private def getQueryResults(query: String): Map[String, JsValue] = {
    log.info("Got: " + query)
    val suggestionStrings = autoCompleter.autoComplete(query)
    Map("results" -> JsArray(suggestionStrings.map(JsString(_))), "query" -> JsString(query))
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

trait StaticRoutes extends Directives {
 // lazy val staticRoute =
 //   path("index.html") { getFromFile("frontend/index.html")} ~
 //   path("frefresh.png") { getFromFile("frontend/refresh.png")} ~
 //   path("search.css") { getFromFile("frontend/search.css")} ~
 //   path("search.js") { getFromFile("frontend/search.js")}
}