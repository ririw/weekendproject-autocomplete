package autocomplete.datasource

import autocomplete.datasource.Search

/**
 * A trait for any search source. A search source must
 * have an iterator that will yield searches. Pretty
 * straight forward.
 *
 * In the future, we probably want to be able to define long
 * pipelines of actions that we apply to search sources. For
 * example, we might want to spell check them, then build out
 * dictionaries of their contents, and so on.
 *
 * There is also a close method. This can be useful where you
 * want to hide some initialization stuff (for example, opening
 * files and things like that, but need the user to eventually
 * close them, eg:
 *
 *  val myDataBaseSource = DataBaseSource.newWithAllTheDefaultStuff()
 *  ...
 *  myDataBaseSource.close()
 *
 * Remember to ensure close works with the composition operator.
 *
 */

trait SearchSource[A] extends AutoCloseable {
  def ~>[Next, SourceNext <: SearchSource[Next]](other: SearchSource[A] => SourceNext): SourceNext

  def iterator: Iterator[A]

  override def close(): Unit
}

/**
 * SearchSources may also mix in error sources, so the user can
 * keep track of failures
 * @tparam E the type for the error
 */
trait ErrorSource[E] {
  this: SearchSource[_] =>
  def errorIterator: Iterator[E]
}

