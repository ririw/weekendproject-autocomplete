package autocomplete.buc

import autocomplete.datasource.{AOLSearchSource, SearchSource}

/**
 * A query here is an array of words, that form a prefix. So the query
 * ["Build", "a"] counts any case starting with "Build a".
 *
 * Naturally, the base query is an empty list. The refinements are in
 * order most to least common. Expansions simply remove one item from
 * the end of the list.
 */
class SearchSourceQuery(searches: AOLSearchSource) extends BucDataSet[Array[String]]{
  override val baseQuery: Array[String] = Array()

  override def expansion(query: Array[String]): Option[Array[String]] = query.length match {
    case 0 => None
    case _ => Some(query.dropRight(1))
  }

  override def refinement(query: Array[String]): Option[Iterator[Array[String]]] = ???

  override def query(query: Array[String]): Long = {
    searches.iterator.count{search =>
      query.zip(search.searchString).forall{case (a, b) => a == b}
    }
  }
}
