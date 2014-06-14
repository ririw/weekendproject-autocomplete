package autocomplete.datasource

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
