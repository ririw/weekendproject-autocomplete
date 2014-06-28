package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLSearchSource
import com.codahale.metrics.ConsoleReporter
import autocomplete.GlobalReg
import java.util.concurrent.TimeUnit

class LazyTrieBenchmarkSpec extends FlatSpec with Matchers {
  it should "benchmark ok" in {
    val reporter = ConsoleReporter.forRegistry(GlobalReg.reg)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
    val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
    val t = searches.prefixTrie
    reporter.report()

    import com.codahale.metrics.MetricRegistry
    val metricsRegistry = new MetricRegistry()
    val reporter2 = ConsoleReporter.forRegistry(metricsRegistry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()

    val meter = metricsRegistry.meter("RPS")
    System.gc()
    val miniDataSet = searchDataSet.iterator
    for (query <- miniDataSet) {
      searches.query(SearchSourceQuery(query.searchString))
      meter.mark()
    }
    reporter2.report()
  }
}
