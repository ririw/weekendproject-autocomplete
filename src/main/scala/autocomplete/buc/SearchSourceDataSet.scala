package autocomplete.buc

import autocomplete.datasource.{Search, AOLSearchSource}
import scala.collection.mutable
import scala.Ordering

/**
 * A query here is an array of words, that form a prefix. So the query
 * ["Build", "a"] counts any case starting with "Build a".
 *
 * Naturally, the base query is an empty list. The refinements are in
 * order most to least common. Expansions simply remove one item from
 * the end of the list.
 */
class SearchSourceDataSet(searches: AOLSearchSource) extends BucDataSet[SearchSourceQuery] with BucDataSetWithMinSup[SearchSourceQuery] {
  override val baseQuery: SearchSourceQuery = SearchSourceQuery(Array())

  /** Expansions are real easy here, we can simply lop off the last word in the query array */
  override def expansion(query: SearchSourceQuery): Option[Iterator[SearchSourceQuery]] = query.expansion

  /**
   * @inheritdoc
   *
   * For this particular algorithm, we form a refinement by going through <b>every</b> search
   * in the data set, and wherever a search matches the query, and that search can also meet a
   * narrower query, we add the least narrow narrowing to a set of counted searches.
   * (for example, for query "build a", we would include "build a website" but exclude "build a").
   * Also, by "least narrow narrowing", I mean that we would return "build a website" over
   * "build a website for my store", because the first one is less narrow than the second.
   *
   * In doing so, we build up a set of searches, each of which is the "next most narrow". Finally
   * drop any search that doesn't meet the minimum support criteria, and return the list of searches
   *
   * @param minSupp the minimum support
   * @param query the query to refine
   */
  override def refinement(minSupp: Long)(query: SearchSourceQuery): Option[Iterator[SearchSourceQuery]] = {
    /* Group by the next item in the search, so for
     * the queries "make me a foo", "make me a bar", "make me an ext", "make me an ext", "make me an ear",  "make me two cats",
     * and query: "make me", we get the lists:
     *   ["make me an ext", "make me an ext", "make me an ear"]
     *   ["make me a foo", "make me a bar"]
     *   ["make me two cats"]
     *
     * We also order this from most to least common, as they are above.
     */
    val groups = mutable.HashMap[SearchSourceQuery, Long]()
    var refinementExists = false
    for (search <- searches.iterator() if query.apply(search) && query.narrowerQueryExists(search)) {
      val newSearch = SearchSourceQuery(search.searchString.take(query.query.length + 1))
      val newCount = groups.getOrElse(newSearch, 0l) + 1l
      groups += (newSearch -> newCount)
      refinementExists = true
    }

    refinementExists match {
      case true  => Some(groups.toIterator.filter(_._2 >= minSupp).map(_._1))
      case false => None
    }
  }

  override def query(query: SearchSourceQuery): Long = searches.iterator().count(query.apply)
}

case class SearchSourceQuery(query: Array[String]) extends AnyVal {
  /**
   * Apply a query to a string. This works by matching up all
   * the items in the query, and pairwise-checking that they are
   * equal. It also checks that the search is at least as long as
   * the query.
   * @param to apply this query to this search
   */
  @inline
  def apply(to: Search): Boolean = {
    query.length <= to.searchString.length &&
      query.zip(to.searchString).forall{case (a, b) => a == b}
  }
  def expansion: Option[Iterator[SearchSourceQuery]] = query.length match {
    case 0 => None
    case _ => Some(List(SearchSourceQuery(query.dropRight(1))).toIterator)
  }

  /**
   * This method tells the caller whether there exists a finer query
   * that would still let through a particular search.
   *
   * @note It is expected that the user has checked that this
   * query would actually let through the search in question, ie
   * query.narrowerQueryExists(SEARCH) yields an undefined answer where
   * query.apply(SEARCH) is false.
   *
   * @param forSearch the search to check against
   * @return true if a narrower search does exist
   */
  @inline
  def narrowerQueryExists(forSearch: Search): Boolean = {
    forSearch.searchString.length > query.length
  }
}

object SearchSourceQuery {
  /** A lexiographic ordering for search queries.
    * @see scala.math.Ordering.ExtraImplicits#seqDerivedOrdering(scala.math.Ordering<T>)
    * */
  implicit val searchOrdering: Ordering[SearchSourceQuery] = new Ordering[SearchSourceQuery] {
    override def compare(x: SearchSourceQuery, y: SearchSourceQuery): Int = {
      val xe = x.query.iterator
      val ye = y.query.iterator

      while (xe.hasNext && ye.hasNext) {
        val res = xe.next().compare(ye.next())
        if (res != 0) return res
      }

      Ordering.Boolean.compare(xe.hasNext, ye.hasNext)
    }
  }
}
