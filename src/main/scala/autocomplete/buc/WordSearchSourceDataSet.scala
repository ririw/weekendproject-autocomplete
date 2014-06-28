package autocomplete.buc

import autocomplete.datasource.{AOLWordSearchSource, Search}
import scala.Ordering

/**
 * A query here is a prefix string, so "Build a" as a query would be
 * true for "Build a website"
 *
 * Naturally, the base query is an empty string. The refinements are in
 * order most to least common. Expansions simply remove one item from
 * the end of the list.
 */
class WordSearchSourceDataSet(searches: AOLWordSearchSource) extends BucDataSet[WordSearchSourceQuery] with BucDataSetWithMinSup[WordSearchSourceQuery] {
  override val baseQuery: WordSearchSourceQuery = WordSearchSourceQuery(List())

  /** Expansions are real easy here, we can simply lop off the last word in the query array */
  override def expansion(query: WordSearchSourceQuery): Option[Iterator[WordSearchSourceQuery]] = query.expansion

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
  override def refinement(minSupp: Long)(query: WordSearchSourceQuery): Option[Iterator[WordSearchSourceQuery]] = {
    /* Group by the next item in the search, so for
         * the queries "make me a foo", "make me a bar", "make me an ext", "make me an ext", "make me an ear",  "make me two cats",
         * and query: "make me", we get the lists:
         *   ["make me an ext", "make me an ext", "make me an ear"]
         *   ["make me a foo", "make me a bar"]
         *   ["make me two cats"]
         *
         * We also order this from most to least common, as they are above.
         */
    val children = prefixTrie.directChildrenCounts(PrefixItem(query.query.toList))
    children.filter(_._2 >= minSupp)
    children.isEmpty match {
      case true  => None
      case false => Some(children.keySet.map{s => WordSearchSourceQuery(s.item.toList)}.toIterator)
    }
  }

  val prefixTrie = new CountingTrie[String](searches.iterator.map{s => PrefixItem(s.search)})
  override def query(query: WordSearchSourceQuery): Long = prefixTrie.get(PrefixItem(query.query))
}

case class WordSearchSourceQuery(query: List[String]) extends AnyVal {
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
  def expansion: Option[Iterator[WordSearchSourceQuery]] = query.length match {
    case 0 => None
    case _ => Some(List(WordSearchSourceQuery(query.dropRight(1))).toIterator)
  }

  override def toString: String = s"WordSearchSourceQuery: ${query.toList.toString()}"

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

  /** Whether this query "contains" another, meaning that this
    * query is MORE GENERAL than another
    * @param anotherQuery the possibly less general query
    * @return
    */
  def contains(anotherQuery: WordSearchSourceQuery): Boolean = {
    anotherQuery.query.length >= this.query.length &&
      anotherQuery.query.zip(this.query).forall{case (a, b) => a == b}
  }
}

object WordSearchSourceQuery {
  /** A lexiographic ordering for search queries.
    * @see scala.math.Ordering.ExtraImplicits#seqDerivedOrdering(scala.math.Ordering<T>)
    * */
  implicit val searchOrdering: Ordering[WordSearchSourceQuery] = new Ordering[WordSearchSourceQuery] {
    override def compare(x: WordSearchSourceQuery, y: WordSearchSourceQuery): Int = {
      val xe = x.query.iterator
      val ye = y.query.iterator

      while (xe.hasNext && ye.hasNext) {
        val res = xe.next().compare(ye.next())
        if (res != 0) return res
      }

      Ordering.Boolean.compare(xe.hasNext, ye.hasNext)
    }
  }

  def makeQuery(query: String*): WordSearchSourceQuery = WordSearchSourceQuery(query.toList)
}
