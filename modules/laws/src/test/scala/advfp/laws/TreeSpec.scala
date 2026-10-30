package advfp.laws

import advfp.typeclasses.{Applicative, Functor, Monad}
import munit.FunSuite

object TreeSpec:
  /** A finite cut of a tree: what `Tree`'s `==` cannot be, so it is what the
    * tests compare.
    */
  final case class Rose[A](value: A, children: List[Rose[A]])

  def leaf[A](a: A): Rose[A] = Rose(a, Nil)

  def mapRose[A, B](rose: Rose[A])(f: A => B): Rose[B] =
    Rose(f(rose.value), rose.children.map(mapRose(_)(f)))

  /** `tree` down to `depth` levels below the root, `width` candidates each.
    * Nothing past the cut is forced.
    */
  def cut[A](tree: Tree[A], depth: Int, width: Int = 4): Rose[A] =
    if depth == 0 then leaf(tree.value)
    else Rose(tree.value, tree.shrinks.take(width).map(cut(_, depth - 1, width)).toList)

  /** Infinite in width and in depth, and fails as soon as a node at 10 or
    * above is built. A cut of depth 3 and width 3 from 0 reaches 9 at most,
    * so it only fails if something forced more than the cut asked for.
    */
  def guarded: Tree[Int] =
    Tree.unfold(0): n =>
      if n >= 10 then throw IllegalStateException(s"forced the node $n")
      else LazyList.from(n + 1)

  /** Every number below `n`, the smallest first. */
  def below(n: Int): Tree[Int] = Tree.unfold(n)(k => LazyList.range(0, k))
end TreeSpec

class TreeSpec extends FunSuite:
  import TreeSpec.*

  private val T = Monad[Tree]

  test("unfold grows the candidates of every node, in shrink's order"):
    val expected =
      Rose(3, List(leaf(0), Rose(1, List(leaf(0))), Rose(2, List(leaf(0), Rose(1, List(leaf(0)))))))
    assertEquals(cut(below(3), depth = 5), expected)

  test("unfold builds only the nodes that are visited, even of an infinite tree"):
    val obtained = cut(guarded, depth = 3, width = 3)
    assertEquals(obtained.children.map(_.value), List(1, 2, 3))
    assertEquals(obtained.children.last.children.last.children.map(_.value), List(7, 8, 9))

  test("pure is a leaf"):
    assertEquals(cut(T.pure(7), depth = 3), leaf(7))

  test("map keeps the shape of the tree"):
    assertEquals(
      cut(T.map(below(3))(_ * 10), depth = 5),
      Rose(
        30,
        List(leaf(0), Rose(10, List(leaf(0))), Rose(20, List(leaf(0), Rose(10, List(leaf(0))))))
      )
    )

  test("map and flatMap force nothing past the cut"):
    val mapped = T.map(guarded)(_ * 10)
    assertEquals(
      cut(mapped, depth = 3, width = 3),
      mapRose(cut(guarded, depth = 3, width = 3))(_ * 10)
    )
    val bound = T.flatMap(guarded)(T.pure)
    assertEquals(cut(bound, depth = 3, width = 3), cut(guarded, depth = 3, width = 3))

  /** Each value bound to a small tree of its own: `10a`, shrinking to `10a + 1`.
    */
  private def tens(a: Int): Tree[Int] = Tree(10 * a, LazyList(Tree(10 * a + 1, LazyList.empty)))

  test("flatMap shrinks the outer value first, then the inner tree"):
    val expected = Rose(
      20,
      List(
        Rose(0, List(leaf(1))),
        Rose(10, List(Rose(0, List(leaf(1))), leaf(11))),
        leaf(21)
      )
    )
    assertEquals(cut(T.flatMap(below(2))(tens), depth = 5), expected)

  test("ap shrinks the function first, then the argument"):
    val ff: Tree[Int => Int] =
      Tree(_ + 100, LazyList(Tree(_ + 200, LazyList.empty)))
    val fa = Tree(1, LazyList(Tree(2, LazyList.empty)))
    val expected = Rose(101, List(Rose(201, List(leaf(202))), leaf(102)))
    assertEquals(cut(T.ap(ff)(fa), depth = 5), expected)

  private def samples: List[(String, Tree[Int])] =
    List("pure" -> T.pure(5), "below(3)" -> below(3), "guarded" -> guarded)

  private val functions: List[(String, Int => Tree[Int])] =
    List("pure" -> T.pure, "tens" -> tens, "below" -> (a => below(a % 4)))

  private def same[A](left: Tree[A], right: Tree[A], clue: => String): Unit =
    assertEquals(cut(left, depth = 3, width = 3), cut(right, depth = 3, width = 3), clue)

  test("left identity: flatMap(pure(a))(f) is f(a)"):
    for a <- List(0, 3); (fName, f) <- functions do
      same(T.flatMap(T.pure(a))(f), f(a), s"at $a with $fName")

  test("right identity: flatMap(m)(pure) is m"):
    for (mName, m) <- samples do same(T.flatMap(m)(T.pure), m, s"at $mName")

  test("associativity of flatMap"):
    for (mName, m) <- samples; (fName, f) <- functions; (gName, g) <- functions do
      same(
        T.flatMap(T.flatMap(m)(f))(g),
        T.flatMap(m)(a => T.flatMap(f(a))(g)),
        s"at $mName with $fName then $gName"
      )

  test("ap agrees with flatMap"):
    val ffs: List[(String, Tree[Int => Int])] =
      List("pure" -> T.pure(_ + 1), "two" -> Tree(_ + 1, LazyList(Tree(_ * 2, LazyList.empty))))
    for (ffName, ff) <- ffs; (faName, fa) <- samples do
      same(T.ap(ff)(fa), T.flatMap(ff)(f => T.map(fa)(f)), s"at $ffName and $faName")

  // ADR 0007, "types of our own": one instance, in Tree's companion.

  test("Functor, Applicative and Monad of Tree are the same instance"):
    val instances = List(Functor[Tree], Applicative[Tree], Monad[Tree])
    assert(instances.forall(_ eq T), s"instances differ: $instances")
end TreeSpec
