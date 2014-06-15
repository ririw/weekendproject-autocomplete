package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLSearchSource

class BucSpec extends FlatSpec with Matchers {
  val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
  val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)
  var buc: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)

  it should "have some items" in {
    val result = buc.apply(SearchSourceQuery.makeQuery("myocutaneous", "flap"))
    result should not be None
    result.get should be > 0l
  }
  it should "Give some refinements" in {
    val grossQuery = SearchSourceQuery.makeQuery("myocutaneous", "flap")
    val result = buc.getRefinements(grossQuery)
    result should not be None
    for ((refinement, count) <- result.get) {
      grossQuery.contains(refinement) should be(true)
      count should be >= 2l
      println(refinement)
    }
  }
  it should "Super break with production data" in {
    val searchDataSetP: AOLSearchSource = AOLSearchSource.productionSearches()
    val searchesP: SearchSourceDataSet = new SearchSourceDataSet(searchDataSetP)
    var bucP: BucComputation[SearchSourceQuery, SearchSourceDataSet] =
      new BucComputation[SearchSourceQuery, SearchSourceDataSet](searchesP, 10)
    val result = buc.apply(SearchSourceQuery.makeQuery())
    result should not be None
    result.get should be > 0l
  }
}
