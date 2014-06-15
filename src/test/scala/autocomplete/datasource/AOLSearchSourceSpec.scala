package autocomplete.datasource

import org.scalatest.{Matchers, FlatSpec}
import org.apache.commons.io.input.AutoCloseInputStream
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class AOLSearchSourceSpec extends FlatSpec with Matchers {
  it should "Read out a couple of test searches" in {
    val search = AOLSearchSource.testingSearches()
    val searches = search.iterator.toSeq
    val firstTestResult = searches(1).searchString
    val anotherResult = searches(14).searchString
    assert(firstTestResult.length == 1)
    assert(firstTestResult(0) == "rentdirect.com")
    assert(anotherResult.length == 3)
    assert(anotherResult(0) == "207")
    assert(anotherResult(1) == "ad2d")
    assert(anotherResult(2) == "530")
    search.close()
  }
  it should "Read out a couple of production searches" in {
    val search = AOLSearchSource.productionSearches()
    val firstTestResult = search.iterator.toSeq.tail.head.searchString
    assert(firstTestResult.length == 1)
    assert(firstTestResult(0) == "rentdirect.com")
    search.close()
  }
  it should "Read out 1000000 items from the test searches" in {
    val search = AOLSearchSource.testingSearches()
    assert(search.iterator.toStream.length == 1000000)
  }

  it should "close the file when it traverses it" in {
    var openFiles: ArrayBuffer[AutoCloseInputStream with AutoCloseable] = new ArrayBuffer()
    def searches(searchSource: List[String]): AOLSearchSource = {
      val args = searchSource.map { path =>
        () => {
          val stream = new AutoCloseInputStream(new GzipInputFileStream(path)) with AutoCloseable
          openFiles += stream
          stream
        }
      }
      new AOLSearchSource(args)
    }
    def search = searches(Configuration.testingPaths)
    val s = search.iterator
    s.toList.filter(_ => false)
    openFiles.foreach{openFile => openFile.read() should be(-1)}
  }
}
