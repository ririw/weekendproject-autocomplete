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
abstract class BucComputation[Query, DataSet <: BucDataSet[Query, _]]
  (val dataSet: DataSet, val minSupp: Long)
  (implicit val queryOrdering: Ordering[Query]) {

  private val nodeMap: TrieMap[Query, Long] = {
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
        dataSet.refinement(currentQuery, minSupp) foreach {
          queryIterator => queryQueue ++= takeRefinements(queryIterator)
        }
      }
    }
    result
  }
  def apply(query: Query): Option[Long] = nodeMap.get(query)

  def takeRefinements(refinements: Iterator[Query]): Iterator[Query]
}

sealed trait BucType

trait KnownQuantityRefiningQuery[Query, DataSet <: BucDataSet[Query, Some[Nothing]]] extends BucType {
  this: BucComputation[Query, DataSet] =>
  def takeRefinements(refinements: Iterator[Query]): Iterator[Query] = {
    refinements.takeWhile{q => dataSet.query(q) >= minSupp}
  }
}

trait UnknownQuantityRefiningQuery[Query, DataSet <: BucDataSet[Query, _]] extends BucType {
  this: BucComputation[Query, DataSet] =>
  def takeRefinements(refinements: Iterator[Query]): Iterator[Query] = {
    refinements.filter{q => dataSet.query(q) >= minSupp}
  }
}

/**
 * This trait represents a dataset for a BUC computation. We need only have a
 * query method defined upon the dataset, which must return a count for the
 * number of items that match that query
 * @tparam Query the datatype for the query
 * @tparam UsesMinSup whether or not this uses the minSup argument. This
 *                    is an Option type containing the Bottom type. Where the type is Some[Nothing]
 *                    it means the minSupp argument is used
 */
trait BucDataSet[Query, UsesMinSup <: Option[Nothing]] {
  /**
   * Run a query over a data set
   * @param query the query to run
   * @return the number of records that fulfill that query
   */
  def query(query: Query): Long

  /**
   * Create an arbitrarily long list of refined queries, in
   * order of most to least supported, ie:
   *  query(refinement.head) > query(refinement.tail.head)
   * and so on.
   *
   * All returned results must have more than minSup
   * items
   *
   * If no refinement is possible, then return None.
   * @param query the base query
   * @param minSupp the minimum number of items needed. If you are using the
   *                data set with a BucComputation extending KnownQuantityRefiningQuery
   *                then all the returned Queries must yield at least minSupp items
   * @return an iterator of queries.
   */
  def refinement(query: Query, minSupp: Long): Option[Iterator[Query]]

  /**
   * Take a query, and return a list of less specific queries
   * in order most to least supported, ie:
   *  query(refinement.head) > query(refinement.tail.head)
   *
   * All returned results must have more than minSup
   * items
   *
   * If no expansion is possible, then return none
   * @param query the base query
   * @param minSupp the minimum number of items needed. If you are using the
   *                data set with a BucComputation extending KnownQuantityRefiningQuery
   *                then all the returned Queries must yield at least minSupp items
   * @return an iterator of queries.
   */
  def expansion(query: Query, minSupp: Long): Option[Iterator[Query]]

  /**
   * The least specific query.
   */
  val baseQuery: Query
}

