package advfp.ch03statereaderwriterandvalidated

import advfp.core.CourseSuite

import Exercise03.{*, given}

class Exercise03Spec extends CourseSuite:

  private val inc: Int => Int = _ + 1
  private val double: Int => Int = _ * 2

  // Both shapes on both sides, and an `Invalid` with more than one error. The
  // laws hold for an `ap` that keeps one side's errors too — that is Either's,
  // and lawful — so accumulation has tests of its own below.
  private val values: List[Checked[Int]] =
    List(valid(1), invalid("v"), Validated.Invalid(NonEmptyList.of("w1", "w2")))

  private val functions: List[Checked[Int => Int]] =
    List(valid(inc), valid(double), invalid("f"), invalid("g"))

  test("ap applies a valid function to a valid value") {
    assertEquals(valid(inc).ap(valid(41)), valid(42))
  }

  test("ap with one side invalid is that side") {
    assertEquals(invalid[Int => Int]("f").ap(valid(1)), invalid[Int]("f"))
    assertEquals(valid(inc).ap(invalid[Int]("v")), invalid[Int]("v"))
  }

  test("ap with both sides invalid keeps every error, the function's first") {
    val both = invalid[Int => Int]("f").ap(Validated.Invalid(NonEmptyList.of("v1", "v2")))
    assertEquals(both, Validated.Invalid(NonEmptyList.of("f", "v1", "v2")))
  }

  test("identity") {
    val F = Applicative[Checked]
    forAllSamples(values): v =>
      assertEquals(F.pure((a: Int) => a).ap(v), v)
  }

  test("homomorphism") {
    val F = Applicative[Checked]
    forAllSamples(List(0, 7, -3)): x =>
      assertEquals(F.pure(inc).ap(F.pure(x)), F.pure(inc(x)))
  }

  test("interchange") {
    val F = Applicative[Checked]
    forAllSamples(functions): u =>
      assertEquals(u.ap(F.pure(5)), F.pure((f: Int => Int) => f(5)).ap(u))
  }

  test("composition") {
    val F = Applicative[Checked]
    val compose = (f: Int => Int) => (g: Int => Int) => f compose g
    forAllSamples(for u <- functions; v <- functions; w <- values yield (u, v, w)): (u, v, w) =>
      assertEquals(F.pure(compose).ap(u).ap(v).ap(w), u.ap(v.ap(w)))
  }

  // --- the form ------------------------------------------------------------------

  test("validateName trims, and rejects a blank name") {
    assertEquals(validateName("  Ada "), valid("Ada"))
    assertEquals(validateName("   "), invalid[String]("name is empty"))
    assertEquals(validateName(""), invalid[String]("name is empty"))
  }

  test("validateAge parses, and checks the minimum") {
    assertEquals(validateAge("18"), valid(18))
    assertEquals(validateAge("17"), invalid[Int]("age must be at least 18"))
    assertEquals(validateAge("old"), invalid[Int]("age is not a number"))
  }

  test("validateEmail wants an @") {
    assertEquals(validateEmail("ada@example.com"), valid("ada@example.com"))
    assertEquals(validateEmail("ada.example.com"), invalid[String]("email has no @"))
  }

  test("validateSignup builds the sign-up when every field is valid") {
    assertEquals(
      validateSignup(" Ada ", "36", "ada@example.com"),
      valid(Signup("Ada", 36, "ada@example.com"))
    )
  }

  test("validateSignup reports one bad field") {
    assertEquals(validateSignup("Ada", "36", "nope"), invalid[Signup]("email has no @"))
  }

  test("validateSignup reports every bad field, in the order of the fields") {
    assertEquals(
      validateSignup("", "x", "nope"),
      Validated.Invalid(NonEmptyList.of("name is empty", "age is not a number", "email has no @"))
    )
    assertEquals(
      validateSignup("", "12", "ada@example.com"),
      Validated.Invalid(NonEmptyList.of("name is empty", "age must be at least 18"))
    )
  }
end Exercise03Spec
