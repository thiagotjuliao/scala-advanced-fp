package advfp.typeclasses

import advfp.typeclasses.Eq.===
import advfp.typeclasses.Semigroup.|+|
import munit.{FunSuite, Location}

class SemigroupSpec extends FunSuite:

  /** Associativity over every triple of the samples. Not enough on its own:
    * a `List` that concatenates in reverse and an `Option` where `None`
    * absorbs are associative too. The tests after each one pin down which
    * associative operation it is.
    */
  private def assertAssociative[A: {Semigroup, Eq}](samples: List[A])(using Location): Unit =
    for a <- samples; b <- samples; c <- samples do
      val left = (a |+| b) |+| c
      val right = a |+| (b |+| c)
      assert(left === right, s"not associative at $a, $b, $c: $left against $right")

  private def assertCombines[A: {Semigroup, Eq}](x: A, y: A, expected: A)(using Location): Unit =
    val obtained = x |+| y
    assert(obtained === expected, s"$x |+| $y gave $obtained, expected $expected")

  private val ints: List[Int] = List(0, 1, -1, 7, Int.MaxValue, Int.MinValue)

  test("Int is associative, across overflow too"):
    assertAssociative(ints)

  test("Int is addition, wrapping on overflow"):
    assertCombines(2, 3, 5)
    assertCombines(Int.MaxValue, 1, Int.MinValue)

  private val options: List[Option[Int]] =
    List(None, Some(0), Some(1), Some(-1), Some(Int.MaxValue))

  test("Option is associative"):
    assertAssociative(options)

  test("Option: None is neutral on both sides"):
    assertCombines[Option[Int]](Some(1), None, Some(1))
    assertCombines[Option[Int]](None, Some(1), Some(1))
    assertCombines[Option[Int]](None, None, None)

  test("Option combines two Somes by their content"):
    assertCombines[Option[Int]](Some(2), Some(3), Some(5))

  private val lists: List[List[Int]] = List(Nil, List(0), List(1), List(0, 1), List(1, 0))

  test("List is associative"):
    assertAssociative(lists)

  test("List concatenates, left operand first"):
    assertCombines(List(1, 2), List(3), List(1, 2, 3))
    assertCombines(Nil, List(1), List(1))
    assertCombines(List(1), Nil, List(1))

  private val eithers: List[Either[Int, Int]] =
    List(Left(0), Left(1), Right(0), Right(1), Right(Int.MaxValue))

  test("Either is associative"):
    assertAssociative(eithers)

  test("Either: the first Left wins, and errors are not combined"):
    assertCombines[Either[Int, Int]](Left(1), Left(2), Left(1))

  test("Either: a Left wins over a Right, on either side"):
    assertCombines[Either[Int, Int]](Left(1), Right(2), Left(1))
    assertCombines[Either[Int, Int]](Right(2), Left(1), Left(1))

  test("Either combines two Rights by their content"):
    assertCombines[Either[Int, Int]](Right(2), Right(3), Right(5))

  test("Either asks nothing of the error type"):
    final class NoInstances
    val either: Either[NoInstances, Int] = Right(1)
    assertEquals(either |+| either, Right(2))
end SemigroupSpec
