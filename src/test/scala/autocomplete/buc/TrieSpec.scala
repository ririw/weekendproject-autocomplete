package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalacheck.Arbitrary._
import com.codahale.metrics.ConsoleReporter
import java.util.concurrent.TimeUnit
import autocomplete.GlobalReg

//TODO: make this a better set of tests.
class TrieSpec extends FlatSpec with Matchers {
  it should "Work!" in {
    val t = new CountingTrie(List(
      PrefixItem(List(1,2,3)),
      PrefixItem(List(1,2,3)),
      PrefixItem(List(1,2)),
      PrefixItem(List(4))).toIterator
    )
    t.get(PrefixItem(List(1,2,3))) should be(2)
    t.get(PrefixItem(List(1,2)))   should be(3)
    t.get(PrefixItem(List(4)))     should be(1)
    t.get(PrefixItem(List(1,2,4))) should be(0)
    t.get(PrefixItem(List()))      should be(4)
    t.directChildrenCounts(PrefixItem(List(1,2))).keySet.map(_.item) should be(Set(List(1,2,3)))
    t.directChildrenCounts(PrefixItem(List(1,2))).values.toSet should be(Set(2))
  }
}

class LazySplittingCountingTrieSpec extends FlatSpec with Matchers with PropertyChecks with GeneratorDrivenPropertyChecks{
  it should "build and work with an empty iterator" in {
    val t = LazySplittingCountingTrie[Char](List().toIterator)
    forAll {s: String =>
      t.get(s) should be(0)
      t.directChildrenCounts(s) should be(List())
    }
  }
  it should "correctly handle empty strings" in {
    val t = LazySplittingCountingTrie[Char](List("".toSeq).toIterator)
    t.get("".toSeq) should be(1)
    t.getNode("".toSeq).map(_.constructFullKey should be("".toSeq))
    forAll {otherstring: String =>
      if ("" != otherstring) {
        t.get(otherstring) should be(0)
      }
    }
  }

  it should "add a string and then find it again" in {
    forAll {s: String =>
      val t = LazySplittingCountingTrie[Char](List(s.toSeq).toIterator)
      t.get(s) should be(1)
      t.getNode(s).map(_.constructFullKey should be(s.toSeq))
      forAll {otherstring: String =>
        if (s != otherstring && !otherstring.startsWith(s)) {
          t.get(otherstring) should be(0)
        }
      }
      t.directChildrenCounts(s) should be(List())
    }
  }
  it should "Add several strings then find them again" in {
    forAll {ss: List[String] =>
      val t = LazySplittingCountingTrie[Char](ss.map(_.toSeq).toIterator)
      ss foreach {s =>
        t.get(s) should be > 0l
        t.getNode(s).map(_.constructFullKey should be(s.toSeq))
      }
    }
  }
  it should "Work with some deliberately compounded strings" in {

    val reporter = ConsoleReporter.forRegistry(GlobalReg.reg)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    //reporter.start(1, TimeUnit.SECONDS)

    forAll {(_prefs: List[String], _posts: List[String]) =>
      val prefs = _prefs.filter(_.length > 0).take(20)
      val posts = _posts.filter(_.length > 0).take(20)
      if (prefs.length > 1 && posts.length > 1) {
        val ss: List[String] = for {
          pref <- prefs
          post <- posts
        } yield pref ++ post
        val t = LazySplittingCountingTrie[Char](ss.map(_.toSeq).toIterator)
        ss foreach {s =>
          t.get(s) should be >= 0l
        }
        prefs foreach {s =>
          t.get(s) should be >= 1l
          t.getNode(s).map(_.constructFullKey should be(s.toSeq))
        }
      }
    }
    reporter.report()
    reporter.stop()
  }
}