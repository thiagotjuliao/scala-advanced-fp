package advfp.ch03statereaderwriterandvalidated

import advfp.core.CourseSuite

import Exercise04.{*, given}

class Exercise04Spec extends CourseSuite:

  private val inc: Int => Int = _ + 1

  private val values: List[Checked[Int]] = List(valid(1), invalid("v"))

  private val functions: List[Checked[Int => Int]] = List(valid(inc), invalid("f"))

  private def isInvalid(v: Validated[?, ?]): Boolean =
    v match
      case Validated.Invalid(_) => true
      case Validated.Valid(_) => false

  private val steps: List[Int => Checked[Int]] =
    List(n => valid(n * 2), n => if n > 1 then invalid("too big") else valid(n), _ => invalid("s"))

  test("ap accumulates, as in exercise 03") {
    assertEquals(
      invalid[Int => Int]("f").ap(invalid[Int]("v")),
      Validated.Invalid(NonEmptyList.of("f", "v"))
    )
  }

  test("flatMap of a valid value continues with it") {
    assertEquals(valid(3).flatMap(n => valid(n + 1)), valid(4))
  }

  test("flatMap of an invalid value is that value, and never calls the function") {
    val result =
      invalid[Int]("v").flatMap(_ => fail("flatMap called its function with no value to give it"))
    assertEquals(result, invalid[Int]("v"))
  }

  // On its own, the short-circuiting flatMap is a lawful monad: it is Either's.
  // What breaks is only its agreement with the accumulating ap.

  test("flatMap: left identity") {
    forAllSamples(steps): f =>
      assertEquals(valid(1).flatMap(f), f(1))
  }

  test("flatMap: right identity") {
    forAllSamples(values): v =>
      assertEquals(v.flatMap(valid), v)
  }

  test("flatMap: associativity") {
    forAllSamples(for v <- values; f <- steps; g <- steps yield (v, f, g)): (v, f, g) =>
      assertEquals(v.flatMap(f).flatMap(g), v.flatMap(a => f(a).flatMap(g)))
  }

  test("ap and flatMap agree whenever one side is valid") {
    val oneSideValid =
      for ff <- functions; fa <- values if !(isInvalid(ff) && isInvalid(fa)) yield (ff, fa)
    forAllSamples(oneSideValid): (ff, fa) =>
      assertEquals(apFromFlatMap(ff)(fa), ff.ap(fa))
  }

  test("the counterexample: ap and flatMap disagree") {
    val (ff, fa) = counterexample
    assertNotEquals(apFromFlatMap(ff)(fa), ff.ap(fa))
  }
end Exercise04Spec
