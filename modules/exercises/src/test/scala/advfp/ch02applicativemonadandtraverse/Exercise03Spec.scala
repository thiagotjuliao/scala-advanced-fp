package advfp.ch02applicativemonadandtraverse

import advfp.core.CourseSuite

import Exercise03.{*, given}
import Exercise03.Rose.leaf

class Exercise03Spec extends CourseSuite:

  // An applicative that records, in order, every value it is handed. Defined
  // here so the order of effects can be checked without leaning on another
  // exercise.
  private type Trace[A] = (List[Int], A)

  private given Applicative[Trace]:
    def pure[A](a: A): Trace[A] = (Nil, a)
    extension [A, B](ff: Trace[A => B])
      def ap(fa: Trace[A]): Trace[B] = (ff._1 ++ fa._1, ff._2(fa._2))

  private val record: Int => Trace[Int] = n => (List(n), n * 10)

  //        1
  //      / | \
  //     2  5  6
  //    / \     \
  //   3   4     7
  private val tree: Rose[Int] =
    Rose(1, List(Rose(2, List(leaf(3), leaf(4))), leaf(5), Rose(6, List(leaf(7)))))

  private val trees: List[Rose[Int]] =
    List(leaf(1), Rose(1, List(leaf(2), leaf(3))), Rose(1, List(Rose(2, List(leaf(3))))), tree)

  test("foldRight visits the tree in pre-order") {
    assertEquals(tree.toList, List(1, 2, 3, 4, 5, 6, 7))
  }

  test("foldRight on a leaf sees one value") {
    assertEquals(leaf(9).foldRight(0)(_ + _), 9)
  }

  test("traverse runs the effects in pre-order") {
    assertEquals(tree.traverse(record)._1, List(1, 2, 3, 4, 5, 6, 7))
  }

  test("traverse rebuilds the same shape around the new values") {
    assertEquals(
      tree.traverse(record)._2,
      Rose(10, List(Rose(20, List(leaf(30), leaf(40))), leaf(50), Rose(60, List(leaf(70)))))
    )
  }

  test("traverse obeys the identity law") {
    forAllSamples(trees): t =>
      assertEquals(t.traverse[Id, Int](a => a), t)
  }

  test("the effects of traverse come in the order foldRight visits") {
    forAllSamples(trees): t =>
      assertEquals(t.traverse(record)._1, t.toList)
  }

  test("map, derived from traverse, transforms every value") {
    assertEquals(Rose(1, List(leaf(2), leaf(3))).map(_ + 1), Rose(2, List(leaf(3), leaf(4))))
  }

  test("Option: ap applies when both are present, and is None otherwise") {
    val F = Applicative[Option]
    assertEquals(F.ap(Some((n: Int) => n + 1))(Some(1)), Some(2))
    assertEquals(F.ap(Some((n: Int) => n + 1))(None), None)
    assertEquals(F.ap(Option.empty[Int => Int])(Some(1)), None)
  }

  test("sequence turns a tree of options into an option of a tree") {
    assertEquals(Rose(Option(1), List(leaf(Option(2)))).sequence, Some(Rose(1, List(leaf(2)))))
    assertEquals(Rose(Option(1), List(leaf(Option.empty[Int]))).sequence, None)
  }

  test("parseAll reads every label of a well-formed tree") {
    assertEquals(
      parseAll(Rose("1", List(Rose("2", List(leaf("3"))), leaf("-4")))),
      Some(Rose(1, List(Rose(2, List(leaf(3))), leaf(-4))))
    )
  }

  test("parseAll is None when a single label, however deep, is not a number") {
    forAllSamples(
      List(
        leaf("x"),
        Rose("x", List(leaf("1"))),
        Rose("1", List(leaf("2"), Rose("3", List(leaf("x"))))),
        Rose("1", List(leaf("x"), leaf("2")))
      )
    ): t =>
      assertEquals(parseAll(t), None)
  }
end Exercise03Spec
