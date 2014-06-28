package autocomplete.datasource

import org.apache.commons.io.input.AutoCloseInputStream

/**
 * A lot like an AOLSearchSource. but words only, rather than chars
 */
class AOLWordSearchSource(inputs: List[() => AutoCloseInputStream]) extends SearchSource[WordSearch] with ErrorSource[SearchFailure] {
  override def iterator: Iterator[WordSearch] = inputs.toIterator.flatMap {input =>
    val thisStream = input()
    val results = scala.io.Source.fromInputStream(thisStream).getLines().map(stringToSearch)
    results.flatMap{r => r.right.toOption}
  }

  protected def stringToSearch(search: String): Either[SearchFailure, WordSearch] = {
    val splitSearch = search.split('\t')
    if (splitSearch.length < 2) {
      println(SearchFailure(search))
      Left(SearchFailure(search))
    } else {
      Right(WordSearch(splitSearch.apply(1).split(' ').toList))
    }
  }

  override def close(): Unit = Unit

  override def ~>[Next, SourceNext <: SearchSource[Next]](other: (SearchSource[WordSearch]) => SourceNext): SourceNext = other(this)

  override def errorIterator: Iterator[SearchFailure] = inputs.toIterator.flatMap { input =>
    val thisStream = input()
    val results = scala.io.Source.fromInputStream(thisStream).getLines().map(stringToSearch)
    results.flatMap{r => r.left.toOption}
  }
}

case class WordSearch(search: List[String]) extends AnyVal

object AOLWordSearchSource {
  def searches(searchSource: List[String]) = {
    val args = searchSource.map {
      path => () => new AutoCloseInputStream(new GzipInputFileStream(path)) with AutoCloseable
    }
    new AOLWordSearchSource(args)
  }
  def productionSearches() = searches(Configuration.productionPaths)
  def testingSearches()    = searches(Configuration.testingPaths)
}


