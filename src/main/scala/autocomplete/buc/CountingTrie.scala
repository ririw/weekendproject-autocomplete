package autocomplete.buc

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
 * One might ask why we do the BucComputation when we've already done this.
 * After all, all we need to do to make the BUC result is to trim the
 * trie and lop off any branch with less than minSup items.
 *
 * The advantage the BUC implementation has is that it can be applied
 * to many different things, as long as they have the query method defined.
 *
 * That said, maybe it's worth writing a clever BUCComputation that
 * works by taking branches off the trie of counts
 *
 * @param items a list of items to insert into the trie
 * @tparam PrefixKey the type for each item in the prefix.
 */
class CountingTrie[PrefixKey](items: Iterator[PrefixItem[PrefixKey]]) {
  private val heads: mutable.HashMap[PrefixKey, MTrieNode] = new mutable.HashMap[PrefixKey, MTrieNode]()
  items.foreach(item => addKey(item.item))

  /**
   * Add an item to the mutable heads set of tries.
   * @param key the key to add
   */
  private def addKey(key: List[PrefixKey]) {
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
   * Get the counts for all the direct children, ie, only direct descendants
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

  /**
   * "Forcibly" traverse a trie. This will always succeed, creating a node
   * if one does exist, and also incrementing all the nodes along the path
   * to the one created or returned.
   * @param fromNode the node to start from. This only works on mutable nodes, for obvious reasons
   * @param prefix the prefix to create/increment.
   */
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

  /**
   * "Gently traverse" a trie, meaning that the trie is not modified (compare to traverseToForciblyAndIncrement)
   *
   * Try to keep this tail-recursive, for speed.
   * @param fromNode the node to start from
   * @param prefix the prefix to search for
   * @tparam N the type of the TrieNode
   * @return a node, of type N, that is at that prefix address, or None if there is no such node
   */
  protected def traverseToGently[N <: TrieNode[N]](fromNode: N, prefix: List[PrefixKey]): Option[N] = {
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

  /**
   * All trie nodes share a few things:
   * a key, and their children. Note that in
   * MTrieNode, children is mutable.
   * @tparam T a self type.
   */
  sealed trait TrieNode[T <: TrieNode[T]] {
    val key: PrefixKey
    val children: MapLike[PrefixKey, T, _]
  }

  /** This is a mutable trie node. */
  case class MTrieNode(key: PrefixKey, var count: Long = 0l, children: mutable.HashMap[PrefixKey, MTrieNode]) extends TrieNode[MTrieNode] {
    /** The freeze method converts this, and all its children, into FTrieNodes */
    def freeze(): FTrieNode = FTrieNode(key, count, children.map{case (k, v) => k -> v.freeze()}.toMap)
  }

  /** This is a Fixed node in the trie, it is immutable */
  case class FTrieNode(key: PrefixKey, count: Long, children: Map[PrefixKey, FTrieNode]) extends TrieNode[FTrieNode]
}

case class PrefixItem[A](item: List[A]) extends AnyVal