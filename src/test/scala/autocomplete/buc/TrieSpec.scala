package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}

//TODO: make this a better set of tests.
class TrieSpec extends FlatSpec with Matchers {
  it should "Work!" in {
    val t = new CountingTrie(List(
      Array(1,2,3),
      Array(1,2,3),
      Array(1,2),
      Array(4)).toIterator
    )
    t.get(List(1,2,3)) should be(2)
    t.get(List(1,2)) should be(3)
    t.get(List(4)) should be(1)
    t.get(List(1,2,4)) should be(0)
    t.directChildrenCounts(List(1,2)).keySet should be(Set(List(1,2,3)))
    t.directChildrenCounts(List(1,2)).values.toSet should be(Set(2))
  }
}
