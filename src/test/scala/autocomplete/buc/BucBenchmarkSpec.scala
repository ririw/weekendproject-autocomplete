package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLSearchSource
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit

class BucBenchmarkSpec extends FlatSpec with Matchers {
  lazy val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
  lazy val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
  lazy val buc: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)

  it should "Benchmark ok" in {
    val searchDataSetP: AOLSearchSource = AOLSearchSource.productionSearches()
    val searchesP: SearchSourceDataSet = new SearchSourceDataSet(searchDataSetP)
    var bucP: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
      new BucComputation[SearchSourceQuery, SearchSourceDataSet](searchesP, 10)
    val result = buc.apply(SearchSourceQuery.makeQuery())

    result should not be None
    result.get should be > 0l


    import com.codahale.metrics.MetricRegistry
    val metricsRegistry = new MetricRegistry()
    val reporter = ConsoleReporter.forRegistry(metricsRegistry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    reporter.start(500, TimeUnit.MILLISECONDS)

    val meter = metricsRegistry.meter("RPS")
    // This is here so I can see what the "base" memory use is
    // after pushing out the stuff used to build the data type.
    System.gc()
    val miniDataSet = searchDataSet.iterator
    for (query <- miniDataSet) {
      bucP.apply(SearchSourceQuery(query.searchString))
      meter.mark()
    }
    reporter.stop()
    reporter.close()
  }
}
