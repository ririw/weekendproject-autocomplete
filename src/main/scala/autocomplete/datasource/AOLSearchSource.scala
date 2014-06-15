package autocomplete.datasource

import java.io.{FileInputStream, InputStream}
import java.util.zip.GZIPInputStream
import org.apache.commons.io.input.AutoCloseInputStream

/**
 * This is for AOL's searches, which take format:
 * AnonID	Query	QueryTime	ItemRank	ClickURL
 * With tab separators.
 *
 * Note - do not close the input streams until all the processing is done. For
 * obvious reasons, these are lazy data structures.
 *
 * The parameter is a list of functions that yield AutoCloseInputStream, ie, that
 * yield iterators into the data set. Note that there is potential for a memory leak/file pointer leak
 * here if you don't finish iterating over the data sets, so always do that.
 */
class AOLSearchSource(inputs: List[() => AutoCloseInputStream]) extends SearchSource[Search] with ErrorSource[SearchFailure] {
  override def iterator: Iterator[Search] = inputs.toIterator.flatMap { input =>
    val thisStream = input()
    val results = scala.io.Source.fromInputStream(thisStream).getLines().map(stringToSearch)
    results.flatMap{r => r.right.toOption}
  }

  protected def stringToSearch(search: String): Either[SearchFailure, Search] = {
    val splitSearch = search.split('\t')
    if (splitSearch.length < 2) {
      println(SearchFailure(search))
      Left(SearchFailure(search))
    } else {
      Right(Search(splitSearch.apply(1).split(' ').toList))
    }
  }
  override def ~>[Next, SourceNext <: SearchSource[Next]](other: (SearchSource[Search]) => SourceNext): SourceNext = {
    other(this)
  }

  override def errorIterator: Iterator[SearchFailure] = inputs.toIterator.flatMap { input =>
    val thisStream = input()
    val results = scala.io.Source.fromInputStream(thisStream).getLines().map(stringToSearch)
    results.flatMap{r => r.left.toOption}
  }

  // Should be covered by AutoCloseInputStream.
  override def close(): Unit = Unit
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
case class Search(searchString: List[String]) extends AnyVal

object AOLSearchSource {
  def searches(searchSource: List[String]) = {
    val args = searchSource.map {
      path => () => new AutoCloseInputStream(new GzipInputFileStream(path)) with AutoCloseable
    }
    new AOLSearchSource(args)
  }
  def productionSearches() = searches(Configuration.productionPaths)
  def testingSearches()    = searches(Configuration.testingPaths)
}

class GzipInputFileStream(path: String) extends InputStream {
  val fileStream = new FileInputStream(path)
  val gzipStream = new GZIPInputStream(fileStream)
  override def read(): Int = gzipStream.read()
  override def read(b: Array[Byte], off: Int, len: Int): Int = gzipStream.read(b, off, len)
  override def skip(n: Long): Long = gzipStream.skip(n)
  override def available(): Int = gzipStream.available()
  override def close() {
    gzipStream.close()
    fileStream.close()
  }
  override def mark(readLimit: Int) {gzipStream.mark(readLimit)}
  override def reset() {gzipStream.reset()}
  override def markSupported: Boolean = {gzipStream.markSupported()}
}