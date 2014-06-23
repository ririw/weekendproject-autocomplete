package autocomplete.buc

import scala.collection.mutable

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
  trait TrieNode[T <: TrieNode[T]] {
    val children: List[TrieBranch]
    val count: Long
    def addNodeForcibly(item: Seq[PrefixKey]): T = {
      children find {
        case TrieBranch(k, _, _) => k.zip(item).takeWhile{case (a,b) => a == b}.length > 0
      } match {
        case None        =>
          val newNode = new TrieBranch(item, 1, Nil)
          this.dup(children = newNode :: children)
        case _ =>
          val newChildren = children map {
            case t@TrieBranch(k, c, _) if k == item => t.copy(count = c+1)
            case   TrieBranch(k, c, ch) =>
              val commonPrefix = k.zip(item).takeWhile{case (a,b) => a == b}.map(_._1)
              val childPrefix = k.drop(commonPrefix.length)
              val itemPrefix = item.drop(commonPrefix.length)
              val childNode = TrieBranch(childPrefix, c, ch)
              val itemNode = TrieBranch(itemPrefix, 1, Nil)
              TrieBranch(k, c+1, childNode :: itemNode :: Nil)
          }
          this.dup(count+1, newChildren)
      }
    }
    def tenderlyTraverseToNode(item: Seq[PrefixKey]): Option[TrieBranch] = {
      children.flatMap {
        case t@TrieBranch(k, _, _) if k == item => Some(t)
        case t@TrieBranch(k, _, _) if item.startsWith(k) => tenderlyTraverseToNode(item.drop(k.length))
        case _ => None
      }.headOption
    }
    def dup(count: Long = count, children: List[TrieBranch] = children): T
  }

  case class TrieBranch(key: Seq[PrefixKey], count: Long, children: List[TrieBranch]) extends TrieNode[TrieBranch] {
    override def dup(count: Long = count, children: List[TrieBranch] = children): TrieBranch =
      this.copy(count=count, children=children)
  }
  case class TrieRoot(count: Long, children: List[TrieBranch]) extends TrieNode[TrieRoot] {
    override def dup(count: Long, children: List[TrieBranch]): TrieRoot =
      this.copy(count=count, children=children)
  }
}

trait MutaZipper[A] {
  val nexts: mutable.ListBuffer[A]
  val prev: Option[A]
}
