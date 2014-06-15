package autocomplete.buc

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/**
 * This class represents some "bottom up cube" style computation.
 *
 * Briefly, the implementer must define a query that may be narrowed
 * arbitrarily. They must also provide a data set, and a minimum support.
 *
 * @tparam Query the query datatype
 * @tparam DataSet the dataset that we run the query over.
 */
abstract class BucComputation[Query, DataSet <: BucDataSet[Query] with BucDataSetRefinable]
  (val dataSet: DataSet, val minSupp: Long)
  (implicit val queryOrdering: Ordering[Query]) {

  /**
   * This is the method that is used to refine the query. We check on the type of
   * the DataSet, and it it's one that uses minSupp we pass minSupp in and set up
   * the call back.
   *
   * Otherwise, we use the non minSupp version.
   */

  private val refineDataSet: Query => Option[Iterator[Query]] = {
    dataSet match {
      case s: BucDataSetWithMinSup[Query] => s.refinement(minSupp)
      case s: BucDataSetWithoutMinSup[Query] => s.refinement
    }
  }

  /**
   * Similarly, takeRefinements is a function that will take a list of queries and then reduce
   * it down to those that are admissible to the problem, ie, those who yield at least minSupp
   * items.
   */
  protected val takeRefinements: Iterator[Query] => Iterator[Query] = {
    dataSet match {
      case s: BucDataSetWithMinSup[Query] => takeRefinements_withMinSupp
      case s: BucDataSetWithoutMinSup[Query] => takeRefinements_withoutMinSupp
    }
  }

  protected def takeRefinements_withMinSupp(refinements: Iterator[Query]): Iterator[Query] = refinements
  protected def takeRefinements_withoutMinSupp(refinements: Iterator[Query]): Iterator[Query] = {
    refinements.filter{q => dataSet.query(q) >= minSupp}
  }

  /**
   * This is the map of all the admissible queries, ie, those who appear minSupp
   * or more times in the dataSet.
   */
  protected val nodeMap: TrieMap[Query, Long] = {
    val result = TrieMap[Query, Long]()
    val queryQueue = new mutable.PriorityQueue[Query]()(queryOrdering)
    queryQueue += dataSet.baseQuery
    while (queryQueue.nonEmpty) {
      val currentQuery = queryQueue.dequeue()
      val numItems = dataSet.query(currentQuery)
      if (numItems >= minSupp) {
        result += (currentQuery -> numItems)
        // Note - this "foreach" extracts from the Option monad
        // and then sends the list to the query Queue's ++= method.
        refineDataSet(currentQuery) foreach {
          queryIterator => queryQueue ++= takeRefinements(queryIterator)
        }
      }
    }
    result
  }
  /** Apply a query, either getting the count for it, or None if it is below minSupp */
  def apply(query: Query): Option[Long] = nodeMap.get(query)
}

/**
 * This trait represents a dataset for a BUC computation. We need only have a
 * query method defined upon the dataset, which must return a count for the
 * number of items that match that query
 * @tparam Query the datatype for the query
 */
trait BucDataSet[Query] {
  /**
   * Run a query over a data set
   * @param query the query to run
   * @return the number of records that fulfill that query
   */
  def query(query: Query): Long

  /** The least specific query. */
  val baseQuery: Query

  /**
   * Expand a query: return a list of less specific queries, or None
   * if no such queries exist
   * @param query the query to expand
   */
  def expansion(query: Query): Option[Iterator[Query]]
}
sealed trait BucDataSetRefinable

/**
 * This is a version of the dataset that will compute a count of the query when
 * it produces the query itself. As such, it should only return those queries
 * that meet the minSupp requirement, ie, dataset.apply(query) >= minSupp
 * @tparam Query the query type
 */
trait BucDataSetWithMinSup[Query] extends BucDataSetRefinable {
  /**
   * Refine the query: return a list of more specific queries, as long as
   * they would yield at least minSupp items.
   *
   * Return None if there are no refinements possible.
   *
   * @param minSupp the minimum support
   * @param query the query to refine
   */
  def refinement(minSupp: Long)(query: Query): Option[Iterator[Query]]
}

/**
 * This is a version of the dataset that cannot easily compute a count
 * for the query, so just return all the proposed queries.
 * @tparam Query theq query type
 */
trait BucDataSetWithoutMinSup[Query] extends BucDataSetRefinable {
  /**
   * Refine the query: return a list of more specific queries,
   * regardless of whether they meet the support criteria
   *
   * Return None if there are no refinements possible.
   *
   * @param query the query to refine
   */
  def refinement(query: Query): Option[Iterator[Query]]
}

