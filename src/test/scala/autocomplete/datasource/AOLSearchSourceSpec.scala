package autocomplete.datasource

import org.specs2.matcher.ShouldMatchers
import org.scalatest.FlatSpec

class AOLSearchSourceSpec extends FlatSpec with ShouldMatchers {
  it should "Read out a couple of test searches" in {
    assert(AOLSearchSource.testingSearches.iterator.toSeq.tail.head.searchString    == Array("rentdirect.com"))
    assert(AOLSearchSource.productionSearches.iterator.toSeq.tail.head.searchString == Array("rentdirect.com"))
    AOLSearchSource.productionSearches.close()
    AOLSearchSource.testingSearches.close()
  }
}
