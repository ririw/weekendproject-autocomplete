package autocomplete

import autocomplete.datasource.{AOLSearchSource, AOLWordSearchSource}
import autocomplete.buc.{LazySplittingCountingTrie, WordSearchSourceQuery, BucComputation, WordSearchSourceDataSet}

trait AutoCompleter {
  def autoComplete(query: String): List[String]
}

object BucAutoCompleter_Test extends AutoCompleter{
  val searchDataSet: AOLWordSearchSource = AOLWordSearchSource.testingSearches()
  val searches: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSet)
  val buc: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
    new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searches, 3)

  def stringToQuery(string: String): WordSearchSourceQuery = WordSearchSourceQuery.makeQuery(string.toLowerCase)
  def queryToString(query: WordSearchSourceQuery): String = query.query.mkString(" ")

  override def autoComplete(query: String): List[String] = {
    val q = stringToQuery(query)
    val result: List[(WordSearchSourceQuery, Long)] = buc.getRefinements(q).getOrElse(List().toIterator).toList.sortBy(-_._2)
    result.take(5).toList.map(_._1.query.mkString(" "))
  }
}
object BucAutoCompleter_Production extends AutoCompleter{
  val searchDataSet: AOLWordSearchSource = AOLWordSearchSource.productionSearches()
  val searches: WordSearchSourceDataSet = new WordSearchSourceDataSet(searchDataSet)
  val buc: BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet] =
    new BucComputation[WordSearchSourceQuery, WordSearchSourceDataSet](searches, 3)

  def stringToQuery(string: String): WordSearchSourceQuery = WordSearchSourceQuery.makeQuery(string.toLowerCase)
  def queryToString(query: WordSearchSourceQuery): String = query.query.mkString(" ")
  override def autoComplete(query: String): List[String] = {
    val q = stringToQuery(query)
    val result: List[(WordSearchSourceQuery, Long)] = buc.getRefinements(q).getOrElse(List().toIterator).toList.sortBy(-_._2)
    result.take(5).toList.map(_._1.query.mkString(" "))
  }
}

object LetterTrieAutoCompleter_Test extends AutoCompleter {
  val searchDataset = AOLSearchSource.testingSearches()
  val searches = LazySplittingCountingTrie.apply(searchDataset.iterator.map(_.searchString.toSeq)).minSupFilter(3)

  override def autoComplete(query: String): List[String] =
    searches.directChildrenCounts(query).sortBy(-_._2).take(5).map(_._1.mkString(""))
}

object LetterTrieAutoCompleter_Production extends AutoCompleter {
  val searchDataset = AOLSearchSource.productionSearches()
  val searches = LazySplittingCountingTrie.apply(searchDataset.iterator.map(_.searchString.toSeq)).minSupFilter(3)

  override def autoComplete(query: String): List[String] =
    searches.directChildrenCounts(query).sortBy(-_._2).take(5).map(_._1.mkString(""))
}