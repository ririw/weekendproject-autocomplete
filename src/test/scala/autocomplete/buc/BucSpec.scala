package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLSearchSource
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit

class BucSpec extends FlatSpec with Matchers {
  val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
  val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
  val buc: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)

  it should "have some items" in {
    val result = buc.apply(SearchSourceQuery.makeQuery("myocutaneous flap"))
    result should not be None
    result.get should be > 0l
  }
  it should "Give some refinements" in {
    val grossQuery = SearchSourceQuery.makeQuery("myocutaneous flap")
    val result = buc.getRefinements(grossQuery)
    result should not be None
    for ((refinement, count) <- result.get) {
      grossQuery.contains(refinement) should be(true)
      count should be >= 2l
      println(refinement)
    }
  }
  it should "Super break with production data" ignore {
    val searchDataSetP: AOLSearchSource = AOLSearchSource.productionSearches()
    val searchesP: SearchSourceDataSet = new SearchSourceDataSet(searchDataSetP)
    val bucP: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
      new BucComputation[SearchSourceQuery, SearchSourceDataSet](searchesP, 10)
    val result = buc.apply(SearchSourceQuery.makeQuery(""))
    result should not be None
    result.get should be > 0l
  }

  it should "Benchmark ok" in {
    val searchDataSetP: AOLSearchSource = AOLSearchSource.productionSearches()
    val searchesP: SearchSourceDataSet = new SearchSourceDataSet(searchDataSetP)
    val bucP: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
      new BucComputation[SearchSourceQuery, SearchSourceDataSet](searchesP, 10)
    val result = buc.apply(SearchSourceQuery.makeQuery(""))

    result should not be None
    result.get should be > 0l


    import com.codahale.metrics.MetricRegistry
    val metricsRegistry = new MetricRegistry()
    val reporter = ConsoleReporter.forRegistry(metricsRegistry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    reporter.start(3, TimeUnit.SECONDS)

    val meter = metricsRegistry.meter("RPS")
    val miniDataSet = searchDataSet.iterator
    for (query <- miniDataSet) {
      bucP.apply(SearchSourceQuery(query.searchString))
      meter.mark()
    }
  }
}
