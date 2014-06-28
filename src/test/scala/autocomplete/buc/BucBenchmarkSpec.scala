package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLWordSearchSource
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit
import autocomplete.GlobalReg

class BucBenchmarkSpec extends FlatSpec with Matchers {
  it should "Benchmark ok" in {
    val reporter = ConsoleReporter.forRegistry(GlobalReg.reg)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    val searchDataSetP: AOLWordSearchSource = AOLWordSearchSource.testingSearches()
    val searchesP: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSetP)
    reporter.report()
    reporter.close()
    val bucP: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
      new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searchesP, 10)
    val result = bucP.apply(WordSearchSourceQuery.makeQuery())

    result should not be None
    result.get should be > 0l


    import com.codahale.metrics.MetricRegistry
    val metricsRegistry = new MetricRegistry()
    val reporter2 = ConsoleReporter.forRegistry(metricsRegistry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()

    val meter = metricsRegistry.meter("RPS")
    // This is here so I can see what the "base" memory use is
    // after pushing out the stuff used to build the data type.
    System.gc()
    val miniDataSet = AOLWordSearchSource.testingSearches().iterator
    for (query <- miniDataSet) {
      bucP.apply(WordSearchSourceQuery(query.search))
      meter.mark()
    }
    reporter2.report()
    reporter2.close()
  }
}
