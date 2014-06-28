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

class LazySplittingCountingTrie[PrefixKey](items: Iterator[Seq[PrefixKey]]) {
  val itemMeter = GlobalReg.reg.meter("LazyTrieItems")
  val trieParts =
    items.foldRight((TrieRoot.empty, 0l)){
      case (item, (tree, count)) =>
        itemMeter.mark()
        if (item.length > 0) (tree.addNodeForcibly(item), count) else (tree, count+1)
    }
  /** The trie! */
  val trie = trieParts._1
  val emptyCounter = trieParts._2
  /** Get the count for a key, zero if it's not found */
  def get(key: Seq[PrefixKey]): Long = if (key.length == 0) emptyCounter else trie.tenderlyTraverseToNode(key).map(_.count).getOrElse(0l)
  /** Get the counts for direct decedents of a key */
  def directChildrenCounts(key: Seq[PrefixKey]): List[(Seq[PrefixKey], Long)] = {
    if (key.length == 0) {
      trie.children.map{node =>
         node.key -> node.count
      }
    } else {
      trie.tenderlyTraverseToNode(key).map {
        node =>
          node.children.map {
            child => node.key ++ child.key -> child.count
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

  /**
   * The TrieNode is pretty much the center of this whole situation.
   * It represents one node in the trie, as you'd expect, but could be
   * either the root or a branch.
   *
   * The root does not have a key, but it does have children and a count,
   * so the only fields that the overall TrieNode has are children and count
   * @tparam T the type for the overriding class
   */
  sealed trait TrieNode[T <: TrieNode[T]] {
    /** The list of all children (always branches) */
    val children: List[TrieBranch]
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
      val thisParent: Option[TrieBranch] = this match {
        case p: TrieBranch => Some(p)
        case p: TrieRoot => None
      }
      assert(item.length > 0)
      children.zipWithIndex find {
        case (TrieBranch(k, _, _, _), i) => k.zip(item).takeWhile{case (a,b) => a == b}.length > 0
      } match {
        case None =>
          val newNode = new TrieBranch(item, 1, Nil, thisParent)
          this.dup(children = newNode :: children)
        case Some((childNode, i)) =>
          val replacementNode = childNode match {
            case t@TrieBranch(k, c, _, _) if k == item => t.copy(count = c + 1)
            case t@TrieBranch(k, _, _, _)
              if item.startsWith(k) => t.addNodeForcibly(item.drop(k.length))
            case TrieBranch(k, c, ch, p) =>
              val commonPrefix = k.zip(item).takeWhile {
                case (a, b) => a == b
              }.map(_._1)
              val childPrefix = k.drop(commonPrefix.length)
              val itemPrefix = item.drop(commonPrefix.length)
              val childNode: TrieBranch = TrieBranch(childPrefix, c, ch, None)
              val itemNode: TrieBranch = TrieBranch(itemPrefix, 1, Nil, None)
              val parent: TrieBranch = if (itemPrefix.length == 0) {
                TrieBranch(commonPrefix, c+1, childNode :: Nil, thisParent)
              }else {
                TrieBranch(commonPrefix, c + 1, childNode :: itemNode :: Nil, thisParent)
              }
              childNode.parent = Some(parent)
              parent.parent = Some(parent)
              parent
          }
          this.dup(children = children.updated(i, replacementNode))
      }
    }

    /**
     * Traverse to a node, or return None if it does not exist. This will do
     * partial matches on nodes.
     * @param item the item to traverse to
     * @return
     */
    def tenderlyTraverseToNode(item: Seq[PrefixKey]): Option[TrieBranch] = {
      children.flatMap {
        case t@TrieBranch(k, _, _, _) if k == item => Some(t)
        case t@TrieBranch(k, _, _, _) if item.startsWith(k) =>
          t.tenderlyTraverseToNode(item.drop(k.length)) match {
            case None => Some(t)
            case t: Some[TrieBranch] => t
          }
        case _ => None
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
    def dup(count: Long = count, children: List[TrieBranch] = children): T
  }
  // This has to use var parent because scala is super lame about mutual recursion and lazy vals.
  case class TrieBranch(key: Seq[PrefixKey], count: Long, children: List[TrieBranch], var parent: Option[TrieBranch]) extends TrieNode[TrieBranch] {
    override def dup(count: Long = count, children: List[TrieBranch] = children): TrieBranch =
      this.copy(count=count, children=children)
  }
  case class TrieRoot(count: Long, children: List[TrieBranch]) extends TrieNode[TrieRoot] {
    override def dup(count: Long, children: List[TrieBranch]): TrieRoot =
      this.copy(count=count, children=children)
  }
  object TrieRoot {
    def empty = TrieRoot(0, Nil)
  }

}
