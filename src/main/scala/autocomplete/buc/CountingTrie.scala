package autocomplete.buc

import scala.annotation.tailrec
import scala.collection.{mutable, MapLike}

/**
 * This is a counting map, based around a prefix trie.
 * It will build up a representation long these lines:
 *
 *    For input: [a,b], [a,c], [a,c], [b,c]
 *
 *                               /-- ('b', 1)
 *             /--- ('a', 3) --<
 *    root --<                  \--- ('c', 2)
 *            \
 *             \------ ('b', 1) ---\
 *                                  \----- ('c', 1)
 *
 * Note that each node of the prefix trie carries the sum of all its
 * children.
 *
 * It operates on lists of prefixes, because their (item :: item :: Nil)
 * structure matches the recursive insertion operations done internally.
 *
 * @param items a list of items to insert into the trie
 * @tparam PrefixKey the type for each item in the prefix.
 */
class CountingTrie[PrefixKey](items: Iterator[PrefixItem[PrefixKey]]) {
  private val heads: mutable.HashMap[PrefixKey, MTrieNode] = new mutable.HashMap[PrefixKey, MTrieNode]()
  items.foreach(item => addKey(item.item))

  def addKey(key: List[PrefixKey]) {
    assert(key.length > 0)
    val child = heads.get(key.head)
    val nextNode = child match {
      case None =>
        val newNode = new MTrieNode(key.head, 0, new mutable.HashMap[PrefixKey, MTrieNode]())
        heads += (key.head -> newNode)
        newNode
      case Some(n) => n
    }
    nextNode.count += 1
    traverseToForciblyAndIncrement(nextNode, key.tail)
  }

  /** The nodes in the trie */
  val trieNodes: Map[PrefixKey, FTrieNode] = heads.map{case (k, v) => k -> v.freeze()}.toMap

  /**
    * Get the count for an item
    * @param key the item
    * @return the count, or zero for not foun
    */
  def get(key: List[PrefixKey]): Long = {
    assert(key.length > 0)
    val child = trieNodes.get(key.head)
    child match {
      case None => 0
      case Some(n) => traverseToGently(n, key.tail).map(_.count).getOrElse(0)
    }
  }

  /**
   * Get the counts for all the direct children, ie, only direct decendants
   * @param key the key to get
   * @return a map from the full prefix item to
   */
  def directChildrenCounts(key: PrefixItem[PrefixKey]): Map[PrefixItem[PrefixKey], Long] = {
    assert(key.item.nonEmpty)
    val child = trieNodes.get(key.item.head)
    child match {
      case None => Map()
      case Some(n) => traverseToGently(n, key.item.tail).
        map{node => node.children.map{case (k, v) => PrefixItem(key.item ++ List(k)) -> v.count}}.getOrElse(Map())
    }
  }
  @inline
  def compareNode(node: TrieNode[_], to: PrefixKey): Boolean = node.key == to

  @tailrec
  private def traverseToForciblyAndIncrement(fromNode: MTrieNode, prefix: List[PrefixKey]) {
    prefix.isEmpty match {
      case true => Unit
      case false =>
        val child = fromNode.children.get(prefix.head)
        val nextNode = child match {
          case None =>
            val newNode = new MTrieNode(prefix.head, 0, new mutable.HashMap())
            fromNode.children += (prefix.head -> newNode)
            newNode
          case Some(k) => k
        }
        nextNode.count += 1
        traverseToForciblyAndIncrement(nextNode, prefix.tail)
    }
  }

  @tailrec
  private def traverseToGently[N <: TrieNode[N]](fromNode: N, prefix: List[PrefixKey]): Option[N] = {
    prefix.isEmpty match {
      case true => Some(fromNode)
      case false =>
        val child = fromNode.children.get(prefix.head)
        child match {
          case None => None
          case Some(c) => traverseToGently(c, prefix.tail)
        }
    }
  }

  trait TrieNode[T <: TrieNode[T]] {
    val key: PrefixKey
    val children: MapLike[PrefixKey, T, _]
  }
  case class MTrieNode(key: PrefixKey, var count: Long = 0l, children: mutable.HashMap[PrefixKey, MTrieNode]) extends TrieNode[MTrieNode] {
    def freeze(): FTrieNode = FTrieNode(key, count, children.map{case (k, v) => k -> v.freeze()}.toMap)
    lazy val childrenMap = children.toMap
  }
  case class FTrieNode(key: PrefixKey, count: Long, children: Map[PrefixKey, FTrieNode]) extends TrieNode[FTrieNode] {
    lazy val childrenMap = children
  }
}

case class PrefixItem[A](item: List[A]) extends AnyVal