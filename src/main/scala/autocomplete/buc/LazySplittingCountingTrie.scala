package autocomplete.buc

import autocomplete.GlobalReg

/**
 *  This is a lot like a counting trie, except that this will
 *  not split up a long chain of entries. For example, for input
 *    "build me a website"
 *    "build me a snack"
 *  you'll get the trie:
 *                           /---- "website", 1
 *  *--- "build me a ", 2 --<
 *                           \---- "snack", 1
 *
 *  This also works over randomly accessible collections, rather than
 *  over specific sequence types.
 *
 */

class LazySplittingCountingTrie[PrefixKey](trie: TrieRoot[PrefixKey], emptyCounter: Long) {
  /** Get the count for a key, zero if it's not found */
  def get(key: Seq[PrefixKey]): Long = if (key.length == 0) emptyCounter else trie.tenderlyTraverseToNode(key).map(_.count).getOrElse(0l)
  /** Get the counts for direct decedents of a key */
  def getNode(key: Seq[PrefixKey]): Option[TrieBranch[PrefixKey]] = {
    if (key.length == 0) None
    else trie.tenderlyTraverseToNode(key)
  }
  def directChildrenCounts(key: Seq[PrefixKey]): List[(Seq[PrefixKey], Long)] = {
    if (key.length == 0) {
      trie.children.map{node =>
         node.constructFullKey -> node.count
      }
    } else {
      trie.tenderlyTraverseToNode(key).map {
        node =>
          node.children.map {
            child => child.constructFullKey -> child.count
          }
      }.getOrElse(List())
    }
  }
  def getParent(key: Seq[PrefixKey]): Option[Seq[PrefixKey]] = {
    if (key.length == 0) {
      None
    } else {
      trie.tenderlyTraverseToNode(key).flatMap(_.parent.map(_.key))
    }
  }
  def minSupFilter(minSup: Long): LazySplittingCountingTrie[PrefixKey] = {
    val newTrie = trie.filter{ _.count >= minSup}
    val newCount = if (emptyCounter < minSup) 0 else emptyCounter
    new LazySplittingCountingTrie[PrefixKey](newTrie, newCount)
  }
}
object LazySplittingCountingTrie {
  val itemMeter = GlobalReg.reg.meter("LazyTrieItems")
  def apply[PrefixKey](items: Iterator[Seq[PrefixKey]]): LazySplittingCountingTrie[PrefixKey] = {
    val trieParts =
      items.foldRight((TrieRoot.empty[PrefixKey], 0l)){
        case (item, (tree, count)) =>
          itemMeter.mark()
          if (item.length > 0) (tree.addNodeForcibly(item), count) else (tree, count+1)
      }
    /** The trie! */
    val trie = trieParts._1
    val emptyCounter = trieParts._2
    new LazySplittingCountingTrie[PrefixKey](trie, emptyCounter)
  }
}


/**
 * The TrieNode is pretty much the center of this whole situation.
 * It represents one node in the trie, as you'd expect, but could be
 * either the root or a branch.
 *
 * The root does not have a key, but it does have children and a count,
 * so the only fields that the overall TrieNode has are children and count
 * @tparam T the type for the overriding class
 */
sealed trait TrieNode[PrefixKey, T <: TrieNode[PrefixKey, T]] {
  /** The list of all children (always branches) */
  val children: List[TrieBranch[PrefixKey]]
  /** The sum of all values below and including this */
  val count: Long

  /**
   * Add an entry to this node, forcibly, meaning that it will create the
   * requisite nodes if needed.
   * @param item the item to add. Note that this method _DOES NOT MATCH ON THIS NODE'S KEY_
   * @return the freshly modified trie.
   */
  /*
  This is reasonably simple. It works by searching amongst the children for any child with the
  same key, or who's key includes part of the item being searched for.

  If no such child is found, one is created and added to this node's set of children and the
  new node returned.

  If the child is found, there are two cases:
    1) the child is a full match - in which case that child is duplicated but with an incremented count
    2) the child is a partial prefix match - in which case that child is replaced by the prefix. The
       replacing node has as children: a new node for the new item, and a node that carries all the
       replaced node's children, and the leftover part of the prefix.

  Then, this yields a new child in either case. This child is added to the list of children for this
  node, and this node has its count incremented.
   */
  def addNodeForcibly(item: Seq[PrefixKey]): T = {
    val thisParent: Option[TrieBranch[PrefixKey]] = this match {
      case p: TrieBranch[PrefixKey] => Some(p)
      case p:   TrieRoot[PrefixKey] => None
    }
    assert(item.length > 0)
    children.zipWithIndex find {
      case (TrieBranch(k, _, _, _), i) => compareKeys(k, item)
    } match {
      case None =>
        val newNode = new TrieBranch[PrefixKey](item, 1, Nil, thisParent)
        this.dup(count = count + 1, children = newNode :: children)
      case Some((childNode, i)) =>
        val replacementNode = childNode match {
          case t@TrieBranch(k, c, _, _) if k == item => t.copy(count = c + 1)
          case t@TrieBranch(k, _, _, _)
            if item.startsWith(k) => t.addNodeForcibly(item.drop(k.length))
          case t@TrieBranch(k, c, ch, p) =>
            splitNode(t, item, thisParent)
        }
        this.dup(count = count + 1, children = children.updated(i, replacementNode))
    }
  }

  private def compareKeys(k: Seq[PrefixKey], item: Seq[PrefixKey]): Boolean = {
    k.length > 0 && item.length > 0 && item.head == k.head
  }

  private def findCommonPrefix(a: Seq[PrefixKey], b: Seq[PrefixKey]): Seq[PrefixKey] = {
    if (a.length == 0 || b.length == 0) Seq()
    else if (a.head == b.head) a.head +: findCommonPrefix(a.tail, b.tail)
    else Seq()
  }
  private def splitNode(t: TrieBranch[PrefixKey],
                        item: Seq[PrefixKey],
                        thisParent: Option[TrieBranch[PrefixKey]]): TrieBranch[PrefixKey] = {
    val k = t.key
    val c = t.count
    val ch = t.children
    val commonPrefix = findCommonPrefix(k, item)
    val childPrefix = k.drop(commonPrefix.length)
    val itemPrefix = item.drop(commonPrefix.length)
    val childNode: TrieBranch[PrefixKey] = TrieBranch[PrefixKey](childPrefix, c, ch, None)
    val itemNode: TrieBranch[PrefixKey] = TrieBranch[PrefixKey](itemPrefix, 1, Nil, None)
    val newNode: TrieBranch[PrefixKey] = if (itemPrefix.length == 0) {
      TrieBranch[PrefixKey](commonPrefix, c+1, childNode :: Nil, thisParent)
    }else {
      TrieBranch[PrefixKey](commonPrefix, c + 1, childNode :: itemNode :: Nil, thisParent)
    }
    childNode.parent = Some(newNode)
    itemNode.parent = Some(newNode)
    newNode.parent = thisParent
    newNode
  }

  /**
   * Traverse to a node, or return None if it does not exist. This will do
   * partial matches on nodes.
   * @param item the item to traverse to
   * @return
   */
  def tenderlyTraverseToNode(item: Seq[PrefixKey]): Option[TrieBranch[PrefixKey]] = {
    children.flatMap {
      case t@TrieBranch(k, _, _, _) if k == item =>
        Some(t)
      case t@TrieBranch(k, _, _, _) if item.startsWith(k) =>
        t.tenderlyTraverseToNode(item.drop(k.length))
      case t@TrieBranch(k, _, _, _) if k.startsWith(item) =>
        Some(t)
      case _ =>
        None
    }.headOption
  }

  /**
   * It turns out that the addNodeForcibly function relies only on duplication with changes
   * to count and the children, never the key - which makes sense, there's no reason to edit
   * the key. Therefore, the only method we need to implement in sub-classes is dup, which
   * copies the object with modified count and children. It bascially follows the struture of
   * the case class copy() method.
   * @param count the count for this node, which includes all nodes below it
   * @param children the children of this node
   * @return
   */
  def dup(count: Long = count, children: List[TrieBranch[PrefixKey]] = children): T
}
// This has to use var parent because scala is super lame about mutual recursion and lazy vals.
case class TrieBranch[PrefixKey](key: Seq[PrefixKey],
                                 count: Long,
                                 children: List[TrieBranch[PrefixKey]],
                                 var parent: Option[TrieBranch[PrefixKey]]) extends TrieNode[PrefixKey, TrieBranch[PrefixKey]] {
  override def dup(count: Long = count, children: List[TrieBranch[PrefixKey]] = children): TrieBranch[PrefixKey] =
    this.copy(count=count, children=children)
  def constructFullKey: Seq[PrefixKey] = parent match {
    case None =>
      key
    case Some(p) =>
      assert(p.ne(this))
      p.constructFullKey ++ key
  }
  override def toString: String = parent match {
    case None => s"TrieBranch(${key.toString}, $count, ${children.toString}, No Parent)@${System.identityHashCode(this)}"
    case Some(p) => s"TrieBranch(${key.toString}, $count, ${children.toString}, ${System.identityHashCode(p)})@${System.identityHashCode(this)}"
  }

  def filter(fn: (TrieBranch[PrefixKey] => Boolean)): Option[TrieBranch[PrefixKey]] = {
    if (fn(this)) Some(this.dup(children=children.flatMap(_.filter(fn))))
    else None
  }
}
case class TrieRoot[PrefixKey](count: Long,
                               children: List[TrieBranch[PrefixKey]]) extends TrieNode[PrefixKey, TrieRoot[PrefixKey]] {
  override def dup(count: Long, children: List[TrieBranch[PrefixKey]]): TrieRoot[PrefixKey] =
    this.copy(count=count, children=children)
  override def toString: String = s"TrieRoot($count, ${children.toString()})@${System.identityHashCode(this)}"

  def filter(fn: (TrieBranch[PrefixKey] => Boolean)): TrieRoot[PrefixKey] = this.dup(children=children.flatMap(_.filter(fn)))
}
object TrieRoot {
  def empty[PrefixKey] = TrieRoot[PrefixKey](0, Nil)
}