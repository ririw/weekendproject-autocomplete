package autocomplete.datasource

import org.specs2.matcher.ShouldMatchers
import org.scalatest.FlatSpec

class AOLSearchSourceSpec extends FlatSpec with ShouldMatchers {
  it should "Read out a couple of test searches" in {
    val search = AOLSearchSource.testingSearches()
    val firstTestResult = search.iterator.toSeq.tail.head.searchString
    assert(firstTestResult.length == 1)
    assert(firstTestResult(0) == "rentdirect.com")
    search.close()
  }
}
