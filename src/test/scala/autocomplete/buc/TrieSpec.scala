package autocomplete.buc

import org.scalatest.{Matchers, FlatSpec}

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
