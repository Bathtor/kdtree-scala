package com.thesamet.spatial

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.{ArrayBuffer, Builder}
import scala.language.implicitConversions
import scala.math.Ordering.Implicits._
import scala.collection.mutable

class KDTree[A] private (root: KDTreeNode[A, Boolean])(implicit ord: DimensionalOrdering[A])
  extends Iterable[A]
  with Serializable {
  override def seq = this

  override def size: Int = root.size

  override def iterator: Iterator[A] = root.toStream.iterator map (_._1)

  def contains(x: A): Boolean = root.get(x).isDefined

  def findNearest[R](x: A, n: Int)(implicit metric: Metric[A, R], numeric: Numeric[R]): Seq[A] =
    root.findNearest(x, n) map (_._1)

  def regionQuery(region: Region[A]): Seq[A] = root.regionQuery(region) map (_._1)
}

class KDTreeMap[A, B] private (root: KDTreeNode[A, B])(implicit ord: DimensionalOrdering[A])
  extends Serializable {

  def empty: KDTreeMap[A, B] = KDTreeMap.empty[A, B](ord)

  def size: Int = root.size

  def iterator: Iterator[(A, B)] = root.toStream.iterator

  def get(x: A): Option[B] = root.get(x)

  def findNearest[R](x: A, n: Int)(implicit
      metric: Metric[A, R],
      numeric: Numeric[R]): Seq[(A, B)] =
    root.findNearest(x, n)

  def regionQuery(region: Region[A]): Seq[(A, B)] = root.regionQuery(region)

  def +[B1 >: B](kv: (A, B1)): KDTreeMap[A, B1] = KDTreeMap.fromSeq(iterator.toSeq ++ Seq(kv))
  def -(key: A): KDTreeMap[A, B] = KDTreeMap.fromSeq(iterator.toSeq.filter(_._1 != key))
}

sealed trait KDTreeNode[A, B] extends Serializable {
  override def toString = toStringSeq(0) mkString "\n"
  def toStringSeq(indent: Int): Seq[String]
  def size: Int
  def isEmpty: Boolean
  def findNearest0[R](x: A, n: Int, skipParent: KDTreeNode[A, B], values: Seq[((A, B), R)])(implicit
      metric: Metric[A, R],
      ord: Ordering[R]): Seq[((A, B), R)]
  def findNearest[R](x: A, n: Int)(implicit metric: Metric[A, R], ord: Ordering[R]): Seq[(A, B)]
  def toStream: LazyList[(A, B)]
  def toSeq: Seq[(A, B)]
  def regionQuery(region: Region[A])(implicit ord: DimensionalOrdering[A]): Seq[(A, B)]

  @tailrec
  final def get(x: A): Option[B] = this match {
    case n @ KDTreeInnerNode(dim, k, v, below, above) =>
      if (k == x) Some(v)
      else (if (n.isAbove(x)) above else below).get(x)
    case n @ KDTreeEmpty() => None
  }
}

case class KDTreeInnerNode[A, B](
    dim: Int,
    key: A,
    value: B,
    below: KDTreeNode[A, B],
    above: KDTreeNode[A, B])(ordering: Ordering[A])
  extends KDTreeNode[A, B]
  with Serializable {

  override def toStringSeq(indent: Int) = {
    val i = "  " * indent

    Seq(i + "size=%d dim=%d key=%s ".format(size, dim, key), i + "Below:") ++ below.toStringSeq(
      indent + 1) ++ Seq(i + "Above:") ++
      above.toStringSeq(indent + 1)
  }

  override val size = below.size + above.size + 1

  override def isEmpty = false

  def isBelow(x: A) = ordering.lt(x, key)

  def isAbove(x: A) = ordering.gt(x, key)

  def isEquiv(x: A) = ordering.equiv(x, key)

  override def findNearest[R](x: A, n: Int)(implicit
      metric: Metric[A, R],
      ord: Ordering[R]): Seq[(A, B)] = {
    // Build initial set of candidates from the smallest subtree containing x with at least n
    // points.
    val minParent = KDTreeNode.findMinimalParent(this, x, withSize = n)
    val values = minParent.toSeq.map { p => (p, metric.distance(x, p._1)) }.sortBy(_._2).take(n)
    findNearest0(x, n, minParent, values) map { _._1 }
  }

  override def findNearest0[R](
      x: A,
      n: Int,
      skipParent: KDTreeNode[A, B],
      values: Seq[((A, B), R)])(implicit
      metric: Metric[A, R],
      ord: Ordering[R]): Seq[((A, B), R)] = {
    if (skipParent eq this) values
    else {
      val myDist = metric.distance(key, x)
      val currentBest = values.last._2

      val newValues = if (myDist < currentBest) {
        (values :+ ((key, value), myDist)) sortBy (_._2) take n
      } else values
      val newCurrentBest = values.last._2

      val dp = metric.planarDistance(dim)(x, key)

      if (dp < newCurrentBest) {
        val values2 = above.findNearest0(x, n, skipParent, newValues)
        below.findNearest0(x, n, skipParent, values2)
      } else if (isAbove(x)) {
        above.findNearest0(x, n, skipParent, newValues)
      } else if (isBelow(x)) {
        below.findNearest0(x, n, skipParent, newValues)
      } else sys.error("Unexpected value!")
    }
  }

  override def regionQuery(region: Region[A])(implicit ord: DimensionalOrdering[A]): Seq[(A, B)] = {
    (if (region.overlapsWith(BelowHyperplane(key, dim)))
       below.regionQuery(region)
     else Nil) ++
      (if (region.contains(key))
         Seq((key, value))
       else Nil) ++
      (if (region.overlapsWith(AboveHyperplane(key, dim)))
         above.regionQuery(region)
       else Nil)
  }

  override def toStream: LazyList[(A, B)] =
    below.toStream #::: LazyList((key, value)) #::: above.toStream

  override def toSeq: Seq[(A, B)] = below.toSeq ++ Seq((key, value)) ++ above.toSeq
}

case class KDTreeEmpty[A, B]() extends KDTreeNode[A, B] with Serializable {
  override def toStringSeq(indent: Int) = Seq(("  " * indent) + "[Empty]")
  override def size = 0
  override def isEmpty = true
  override def findNearest[R](x: A, n: Int)(implicit
      metric: Metric[A, R],
      ord: Ordering[R]): Seq[(A, B)] =
    Seq.empty
  override def findNearest0[R](
      x: A,
      n: Int,
      skipParent: KDTreeNode[A, B],
      values: Seq[((A, B), R)])(implicit metric: Metric[A, R], ord: Ordering[R]): Seq[((A, B), R)] =
    values
  override def toSeq: Seq[(A, B)] = Seq.empty
  override def toStream: LazyList[(A, B)] = LazyList.empty
  override def regionQuery(region: Region[A])(implicit ord: DimensionalOrdering[A]): Seq[(A, B)] =
    Seq.empty
}

object KDTreeNode {
  def buildTreeNode[A, B](depth: Int, points: Seq[(A, B)])(implicit
      ord: DimensionalOrdering[A]): KDTreeNode[A, B] = {
    def findSplit(points: Seq[(A, B)], i: Int): ((A, B), Seq[(A, B)], Seq[(A, B)]) = {
      val sp = points.sortBy(_._1)(ord.orderingBy(i))
      val medIndex = sp.length / 2
      (sp(medIndex), sp.take(medIndex), sp.drop(medIndex + 1))
    }

    if (points.isEmpty) KDTreeEmpty[A, B]()
    else {
      val i = depth % ord.dimensions
      val ((key, value), below, above) = findSplit(points, i)
      KDTreeInnerNode(
        i,
        key,
        value,
        buildTreeNode(depth + 1, below),
        buildTreeNode(depth + 1, above))(ord.orderingBy(i))
    }
  }

  @tailrec
  def findMinimalParent[A, B](
      node: KDTreeInnerNode[A, B],
      x: A,
      withSize: Int): KDTreeInnerNode[A, B] = if (node.key == x) node
  else {
    val next = if (node.isBelow(x)) node.below else node.above
    if (next.size < withSize) node
    else findMinimalParent(next.asInstanceOf[KDTreeInnerNode[A, B]], x, withSize)
  }
}

object KDTree {
  def apply[A](points: A*)(implicit ord: DimensionalOrdering[A]): KDTree[A] = fromSeq(points)

  def fromSeq[A](points: Seq[A])(implicit ord: DimensionalOrdering[A]): KDTree[A] = {
    assert(ord.dimensions >= 1)
    new KDTree(KDTreeNode.buildTreeNode(0, points map { (_, true) }))
  }

  def newBuilder[A](implicit ord: DimensionalOrdering[A]): Builder[A, KDTree[A]] =
    Seq.newBuilder[A] mapResult (x => KDTree.fromSeq(x))
}

object KDTreeMap {

  def empty[A, B](implicit ord: DimensionalOrdering[A]): KDTreeMap[A, B] = KDTreeMap()

  def apply[A, B](points: (A, B)*)(implicit ord: DimensionalOrdering[A]): KDTreeMap[A, B] = fromSeq(
    points)

  def fromSeq[A, B](points: Seq[(A, B)])(implicit ord: DimensionalOrdering[A]): KDTreeMap[A, B] =
    new KDTreeMap(KDTreeNode.buildTreeNode(0, points))
}
