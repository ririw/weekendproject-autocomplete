package autocomplete.buc

import autocomplete.datasource.{Search, AOLSearchSource}
import scala.collection.mutable

/**
 * A query here is an array of words, that form a prefix. So the query
 * ["Build", "a"] counts any case starting with "Build a".
 *
 * Naturally, the base query is an empty list. The refinements are in
 * order most to least common. Expansions simply remove one item from
 * the end of the list.
 */
class SearchSourceDataSet(searches: AOLSearchSource) extends BucDataSet[SearchSourceQuery]{
  override val baseQuery: SearchSourceQuery = SearchSourceQuery(Array())

  override def expansion(query: SearchSourceQuery): Option[Iterator[SearchSourceQuery]] = query.expansion

  override def refinement(query: SearchSourceQuery): Option[Iterator[SearchSourceQuery]] = {
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
      case true  => Some(groups.toVector.sortBy(_._2).map(_._1).toIterator)
      case false => None
    }
  }

  override def query(query: SearchSourceQuery): Long = searches.iterator().count(query.apply)
}

case class SearchSourceQuery(query: Array[String]) extends AnyVal {
  @inline
  def apply(to: Search): Boolean = {
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
   * Note that it is expected that the user has checked that this
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