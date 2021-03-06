package autocomplete.buc

import org.scalatest._
import autocomplete.datasource.AOLSearchSource
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

class SearchSourceDataSetSpec extends FlatSpec with Matchers {
  val searchDataSet = AOLSearchSource.testingSearches()

  val searches = new SearchSourceDataSet(searchDataSet)

  it should "yield the right base query" in {
    searches.baseQuery.query.length should be(0)
  }
  it should "yield some results, and do so many times." in {
    val aSearch = SearchSourceQuery("integrated real")
    searches.query(aSearch) should be > 0l
    searches.query(aSearch) should be > 0l
  }
  "expansion" should "return nothing when expanding the base query" in {
    searches.expansion(searches.baseQuery) should be(None)
  }
  it should "correctly expand a query, ie, the expand property holds" in {
    val aSearch = SearchSourceQuery("integrated real")
    searches.query(aSearch) should be > 0l
    val expansion = searches.expansion(aSearch)
    expansion should be !== None
    expansion.get.nonEmpty should be(true)
    val expandedSearch = expansion.get.next()
    searches.query(expandedSearch) should be > searches.query(aSearch)
  }
  "refinement" should "correctly refine a query" in {
    val aSearch = SearchSourceQuery("integrated")
    searches.query(aSearch) should be > 1l
    val refinement = searches.refinement(1)(aSearch)
    refinement should be !== None
    refinement.get.nonEmpty should be(true)
    refinement.get.foreach { refinedSearch =>
      println(refinedSearch)
      searches.query(refinedSearch) should be >= 1l
      searches.query(refinedSearch) should be < searches.query(aSearch)
      refinedSearch.query.length should be > aSearch.query.length
      println(refinedSearch)
    }
  }
  it should "eventually stop refining" ignore {
    import ExecutionContext.Implicits.global
    val foundRefinement = Future {
      val searches = new SearchSourceDataSet(searchDataSet)
      val aSearch = SearchSourceQuery("integrated")
      var refinement = aSearch
      while (searches.refinement(0)(refinement) != None) {
        refinement = searches.refinement(0)(refinement).get.next()
      }
    }
    // If we haven't found a refinment within three seconds, this
    // will throw TimeoutError
    Await.ready(foundRefinement, Duration(30, TimeUnit.SECONDS))
  }
}
