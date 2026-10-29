package advfp.typeclasses

import advfp.typeclasses.Eq.===
import munit.{FunSuite, Location}

class EqSpec extends FunSuite:

  /** The three laws, over every pair and triple of the samples. The samples
    * hold the edges of their type, and values that are equal but built apart:
    * reflexivity checked only on `a` against itself would pass for reference
    * equality too.
    */
  private def assertEquivalence[A: Eq](samples: List[A])(using Location): Unit =
    samples.foreach: a =>
      assert(a === a, s"not reflexive at $a")
    for a <- samples; b <- samples do assert(!(a === b) || (b === a), s"not symmetric at $a, $b")
    for a <- samples; b <- samples; c <- samples do
      assert(!(a === b && b === c) || (a === c), s"not transitive at $a, $b, $c")

  private def assertDistinct[A: Eq](x: A, y: A)(using Location): Unit =
    assert(!(x === y), s"$x and $y compared equal")
    assert(!(y === x), s"$y and $x compared equal")

  test("Int is an equivalence"):
    assertEquivalence(List(0, 1, -1, Int.MaxValue, Int.MinValue))

  test("Int tells different values apart"):
    assertDistinct(1, 2)

  private val options: List[Option[Int]] = List(None, Some(0), Some(1), Option(0))

  test("Option is an equivalence"):
    assertEquivalence(options)

  test("Option: two Nones are equal"):
    assert((None: Option[Int]) === None)

  test("Option tells None from Some, and Somes by their content"):
    assertDistinct[Option[Int]](None, Some(0))
    assertDistinct[Option[Int]](Some(1), Some(2))

  private val lists: List[List[Int]] =
    List(Nil, List(0), List(1), List(0, 1), List(1, 0), List(0, 0), (0 to 1).toList)

  test("List is an equivalence"):
    assertEquivalence(lists)

  test("List: two empty lists are equal"):
    assert((Nil: List[Int]) === Nil)

  test("List: equal lists built apart are equal"):
    assert(List(1, 2, 3) === (1 to 3).toList)

  test("List tells a list from its prefix, in both orders"):
    assertDistinct(List(1), List(1, 2))
    assertDistinct(Nil, List(1))

  test("List tells lists apart when only the last element differs"):
    assertDistinct(List(1, 2, 3), List(1, 2, 4))

  // Long enough to overflow the default stack with one frame per element.
  private val long = 100_000

  test("List compares long lists in constant stack"):
    assert(List.range(0, long) === (0 until long).toList)
    assertDistinct(List.range(0, long), List.range(0, long - 1) :+ -1)

  private val eithers: List[Either[Int, Int]] =
    List(Left(0), Left(1), Right(0), Right(1), Left(0).withRight[Int])

  test("Either is an equivalence"):
    assertEquivalence(eithers)

  test("Either: equal Lefts are equal"):
    assert((Left(1): Either[Int, Int]) === Left(1))

  test("Either tells Left from Right, and each side by its content"):
    assertDistinct[Either[Int, Int]](Left(1), Right(1))
    assertDistinct[Either[Int, Int]](Left(1), Left(2))
    assertDistinct[Either[Int, Int]](Right(1), Right(2))
end EqSpec
