package autocomplete.buc

import scala.annotation.tailrec
import scala.collection.{MapLike, mutable}

class CountingTrie[PrefixKey](items: Iterator[Array[PrefixKey]]) {
  private val heads = new mutable.HashMap[PrefixKey, MTrieNode]()
  items.foreach(item => addKey(item.toList))

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

  val trieNodes = heads.map{case (k, v) => k -> v.freeze()}.toMap
  def get(key: List[PrefixKey]): Long = {
    assert(key.length > 0)
    val child = trieNodes.get(key.head)
    child match {
      case None => 0
      case Some(n) => traverseToGently(n, key.tail).map(_.count).getOrElse(0)
    }
  }

  def directChildrenCounts(key: List[PrefixKey]): Map[List[PrefixKey], Long] = {
    assert(key.length > 0)
    val child = trieNodes.get(key.head)
    child match {
      case None => Map()
      case Some(n) => traverseToGently(n, key.tail).
        map{node => node.children.map{case (k, v) => key ++ List(k) -> v.count}}.getOrElse(Map())
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
