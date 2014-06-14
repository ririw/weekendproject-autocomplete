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
class BucComputation[Query, DataSet <: BucDataSet[Query]]
  (val dataSet: DataSet, minSupp: Long)
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
        dataSet.refinement(currentQuery) foreach {queryIterator =>
          val queries = queryIterator.takeWhile{q => dataSet.query(q) >= minSupp}
          queryQueue ++= queries}
      }
    }
    result
  }
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

  /**
   * Create an arbitrarily long list of refined queries, in
   * order of most to least supported, ie:
   *  query(refinement.head) > query(refinement.tail.head)
   * and so on.
   *
   * If no refinement is possible, then return None.
   * @param query the base query
   * @return an iterator of queries.
   */
  def refinement(query: Query): Option[Iterator[Query]]

  /**
   * Take a query, and return a list of less specific queries
   * in order most to least supported, ie:
   *  query(refinement.head) > query(refinement.tail.head)
   *  
   * If no expansion is possible, then return none
   * @param query the base query
   * @return an iterator of queries.
   */
  def expansion(query: Query): Option[Iterator[Query]]

  /**
   * The least specific query.
   */
  val baseQuery: Query
}

