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
class SearchSourceDataSet(searches: AOLSearchSource) extends BucDataSet[Array[String]]{
  override val baseQuery: Array[String] = Array()

  override def expansion(query: Array[String]): Option[Iterator[Array[String]]] = query.length match {
    case 0 => None
    case _ => Some(List(query.dropRight(1)).toIterator)
  }

  override def refinement(query: Array[String]): Option[Iterator[Array[String]]] = {
    /* Group by the next item in the search, so for
     * the queries "make me a foo", "make me a bar", "make me an ext", "make me an ext", "make me an ear",  "make me two cats",
     * and query: "make me", we get the lists:
     *   ["make me an ext", "make me an ext", "make me an ear"]
     *   ["make me a foo", "make me a bar"]
     *   ["make me two cats"]
     *
     * We also order this from most to least common, as they are above.
     */
    val groups = mutable.HashMap[Array[String], Long]()
    var refinementExists = false
    for (search <- searches.iterator() if applyFilter(query, search) && search.searchString.length > query.length) {
      val newSearch = search.searchString.take(query.length + 1)
      val newCount = groups.getOrElse(newSearch, 0l) + 1l
      groups += (newSearch -> newCount)
      refinementExists = true
    }

    refinementExists match {
      case true  => Some(groups.toVector.sortBy(_._2).map(_._1).toIterator)
      case false => None
    }
  }

  override def query(query: Array[String]): Long = {
    searches.iterator().count(applyFilter(query, _))
  }

  @inline
  private def applyFilter(query: Array[String], to: Search): Boolean = {
    query.zip(to.searchString).forall{case (a, b) => a == b}
  }
}

