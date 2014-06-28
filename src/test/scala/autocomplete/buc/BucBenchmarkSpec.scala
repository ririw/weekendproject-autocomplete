package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLWordSearchSource
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit

class BucBenchmarkSpec extends FlatSpec with Matchers {
  lazy val searchDataSet: AOLWordSearchSource = AOLWordSearchSource.testingSearches()
  lazy val searches: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSet)
  lazy val buc: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
    new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searches, 3)

  it should "Benchmark ok" in {
    val searchDataSetP: AOLWordSearchSource = AOLWordSearchSource.productionSearches()
    val searchesP: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSetP)
    val bucP: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
      new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searchesP, 10)
    val result = buc.apply(WordSearchSourceQuery.makeQuery())

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
      bucP.apply(WordSearchSourceQuery(query.search))
      meter.mark()
    }
    reporter.stop()
    reporter.close()
  }
}
