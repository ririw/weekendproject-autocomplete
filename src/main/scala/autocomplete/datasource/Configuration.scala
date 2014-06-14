package autocomplete.datasource

import reactive.api.{MainActors, ReactiveApi}
import reactive.socket.ReactiveServer
import akka.actor.ActorSystem
import akka.io.{IO, Tcp}
import java.net.InetSocketAddress
import spray.can.Http
import scala.collection.JavaConversions
import com.typesafe.config.ConfigValue
import scala.collection.convert.Wrappers.JListWrapper


object Configuration {
  import com.typesafe.config.ConfigFactory

  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  val productionPaths: List[String] = JListWrapper(
    config.getList("search-dataset.production-paths").unwrapped()).
    map(_.asInstanceOf[String]).toList
  val testingPaths = JListWrapper(
    config.getList("search-dataset.testing-paths").unwrapped()).
    map(_.asInstanceOf[String]).toList
}
