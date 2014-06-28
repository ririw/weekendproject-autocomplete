package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.{WordSearch, AOLWordSearchSource}
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit

class BucSpec extends FlatSpec with Matchers {
  val searchDataSet: AOLWordSearchSource = AOLWordSearchSource.testingSearches()
  val searches: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSet)
  val buc: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
    new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searches, 3)

  it should "have some items" in {
    val result = buc.apply(WordSearchSourceQuery.makeQuery("myocutaneous", "flap"))
    result should not be None
    result.get should be > 0l
  }
  it should "Give some refinements" in {
    val grossQuery = WordSearchSourceQuery.makeQuery("myocutaneous", "flap")
    val result = buc.getRefinements(grossQuery)
    result should not be None
    for ((refinement, count) <- result.get) {
      grossQuery.contains(refinement) should be(true)
      count should be >= 2l
    }
  }

  it should "Benchmark ok" in {
    val searchDataSetP: AOLWordSearchSource = AOLWordSearchSource.testingSearches()
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
    reporter.start(3, TimeUnit.SECONDS)

    val meter = metricsRegistry.meter("RPS")
    val miniDataSet = searchDataSet.iterator
    for (query: WordSearch <- miniDataSet) {
      bucP.apply(WordSearchSourceQuery(query.search))
      meter.mark()
    }
    reporter.stop()
  }
}
