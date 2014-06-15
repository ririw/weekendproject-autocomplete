package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import autocomplete.datasource.AOLSearchSource

class BucSpec extends FlatSpec with Matchers {
  val searchDataSet: AOLSearchSource = AOLSearchSource.testingSearches()
  val searches: SearchSourceDataSet = new SearchSourceDataSet(searchDataSet)

  it should "Initalize without crashing" in {
    new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 3)
  }

  it should "have some items" in {
    val buc = new BucComputation[SearchSourceQuery, SearchSourceDataSet](searches, 2)
    val result = buc(SearchSourceQuery.makeQuery("myocutaneous", "flap"))
    result should not be None
    result.get should be > 0l
  }
}
