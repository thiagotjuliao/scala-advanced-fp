package advfp.ch01typeclassesandlaws

import advfp.core.CourseSuite

import Exercise03.{*, given}
import Exercise03.Tree.*

class Exercise03Spec extends CourseSuite:

  private type Result[A] = Either[String, A]

  private val trees: List[Tree[Int]] = List(
    Leaf(1),
    Branch(Leaf(1), Leaf(2)),
    Branch(Branch(Leaf(1), Leaf(2)), Leaf(3)),
    Branch(Leaf(4), Branch(Leaf(5), Branch(Leaf(6), Leaf(7))))
  )

  private val results: List[Result[Int]] = List(Right(0), Right(41), Left("nope"), Left(""))

  private val double: Int => Int = _ * 2
  private val describe: Int => String = n => s"<$n>"

  test("a tree maps every leaf and keeps its shape") {
    assertEquals(
      Functor[Tree].map(Branch(Leaf(1), Branch(Leaf(2), Leaf(3))))(double),
      Branch(Leaf(2), Branch(Leaf(4), Leaf(6)))
    )
  }

  test("a tree obeys the identity law") {
    forAllSamples(trees): t =>
      assertEquals(Functor[Tree].map(t)(identity), t)
  }

  test("a tree obeys the composition law") {
    forAllSamples(trees): t =>
      val F = Functor[Tree]
      assertEquals(F.map(F.map(t)(double))(describe), F.map(t)(double andThen describe))
  }

  test("Either maps a Right") {
    assertEquals(Functor[Result].map(Right(20))(double), Right(40))
  }

  test("Either leaves a Left alone") {
    assertEquals(Functor[Result].map(Left("nope"))(double), Left("nope"))
  }

  test("Either obeys the identity law") {
    forAllSamples(results): r =>
      assertEquals(Functor[Result].map(r)(identity), r)
  }

  test("Either obeys the composition law") {
    forAllSamples(results): r =>
      val F = Functor[Result]
      assertEquals(F.map(F.map(r)(double))(describe), F.map(r)(double andThen describe))
  }
end Exercise03Spec
