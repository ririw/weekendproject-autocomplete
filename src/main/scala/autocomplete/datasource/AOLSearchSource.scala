package autocomplete.datasource

import java.io.{FileInputStream, InputStream}
import java.util.zip.GZIPInputStream

/**
 * This is for AOL's searches, which take format:
 * AnonID	Query	QueryTime	ItemRank	ClickURL
 * With tab separators.
 *
 * Note - do not close the input streams until all the processing is done. For
 * obvious reasons, these are lazy data structures.
 *
 * The parameter is a list of inputstreams and close methods.
 */
class AOLSearchSource(inputs: List[(InputStream, () => Unit)]) extends SearchSource[Search] with ErrorSource[SearchFailure] {
  override def iterator: Iterator[Search] = inputs.toIterator.flatMap { input =>
    val results = scala.io.Source.fromInputStream(input._1).getLines().map(stringToSearch)
    results.flatMap{r => r.right.toOption}
  }

  protected def stringToSearch(search: String): Either[SearchFailure, Search] = {
    val splitSearch = search.split('\t')
    if (splitSearch.length < 2) {
      println(SearchFailure(search))
      Left(SearchFailure(search))
    } else {
      Right(Search(splitSearch.apply(1).split(' ')))
    }
  }
  override def ~>[Next, SourceNext <: SearchSource[Next]](other: (SearchSource[Search]) => SourceNext): SourceNext = {
    other(this)
  }

  /**
   * The close method here will call the input's close method.
   */
  override def close(): Unit = inputs.foreach{_._2()}

  override def errorIterator: Iterator[SearchFailure] = inputs.toIterator.flatMap { input =>
    val results = scala.io.Source.fromInputStream(input._1).getLines().map(stringToSearch)
    results.flatMap{r => r.left.toOption}
  }
}

/**
 * We use an entire case class for failures for a few reasons.
 * Firstly, better type safety.
 * Secondly, with AnyVal, it's going to be only the error causing string,
 * rather than having to create something like "The error string was: " + string
 * which would mean interning more strings. And that's bad
 * @param search the string in question
 */

case class SearchFailure(search: String) extends AnyVal

/**
 * The successful case.
 * @param searchString the list of search terms.
 */
case class Search(searchString: Array[String]) extends AnyVal

object AOLSearchSource {
  def searches(searchSource: List[String]) = {
    val args = searchSource.map {
      path =>
        val fileStream = new FileInputStream(path)
        val gzipStream = new GZIPInputStream(fileStream)
        (gzipStream, {() =>
          gzipStream.close()
          fileStream.close()})
    }
    new AOLSearchSource(args)
  }
  def productionSearches() = searches(Configuration.productionPaths)
  def testingSearches()    = searches(Configuration.testingPaths)
}