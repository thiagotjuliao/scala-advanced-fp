package advfp.laws

import munit.{FailException, FunSuite}

class PropAssertionsSpec extends FunSuite with PropAssertions:
  import Prop.forAll
  import PropAssertions.describe

  private val fixed = Config(seed = 42)

  private def report(failure: Failure, exhausted: Boolean = false): Report[Int] =
    Report(
      seed = 123L,
      size = Size.of(5),
      test = 12,
      original = 700,
      shrunk = 100,
      failure = failure,
      shrinks = 7,
      exhausted = exhausted
    )

  /** Not inline in the tests: munit's message quotes the source around the
    * call, so an assertion on the next line would find its own text in it.
    */
  private def failureOf[A](prop: Prop[A], config: Config): FailException =
    intercept[FailException](assertHolds(prop, config))

  test("a property that holds does not fail the test"):
    assertHolds(forAll(Gen.choose(0, 10))(_ => true), fixed)

  test("a property that fails fails the test, with the report as its message"):
    val failure = failureOf(forAll(Gen.choose(0, 1000))(x => (x min 100) <-> x), fixed)
    assert(failure.getMessage.contains("shrunk:   101"), failure.getMessage)
    assert(failure.getMessage.contains("lhs:      100"), failure.getMessage)
    assert(failure.getMessage.contains("rhs:      101"), failure.getMessage)

  test("assertHolds runs with the config it is given"):
    val prop = forAll(Gen.choose(0, 1000))(_ < 100)
    val failure = failureOf(prop, fixed.copy(maxShrinks = 0))
    assert(failure.getMessage.contains("stopped by the limit"), failure.getMessage)

  test("what the property threw is the failure's cause"):
    val error = IllegalStateException("boom")
    val failure = failureOf(forAll(Gen.const(1))(_ => throw error), fixed)
    assertEquals(failure.getCause, error)

  test("a failure by inequality has no cause"):
    val failure = failureOf(forAll(Gen.const(1))(_ <-> 2), fixed)
    assertEquals(failure.getCause, null)

  test("a law's report names every field, with lhs before rhs"):
    assertNoDiff(
      describe(report(Failure.Unequal("100", "101"))),
      """|failed at test 12
         |original: 700
         |shrunk:   100 (7 steps)
         |lhs:      100
         |rhs:      101
         |recheck:  seed 123L, size 5
         |""".stripMargin
    )

  test("a Boolean property's report has no sides"):
    assertNoDiff(
      describe(report(Failure.Unequal("", ""))),
      """|failed at test 12
         |original: 700
         |shrunk:   100 (7 steps)
         |recheck:  seed 123L, size 5
         |""".stripMargin
    )

  test("a report of an exception shows the exception"):
    assertNoDiff(
      describe(report(Failure.Threw(IllegalStateException("boom")))),
      """|failed at test 12
         |original: 700
         |shrunk:   100 (7 steps)
         |threw:    java.lang.IllegalStateException: boom
         |recheck:  seed 123L, size 5
         |""".stripMargin
    )

  test("an exhausted shrink says its result may not be a minimum"):
    val described = describe(report(Failure.Unequal("", ""), exhausted = true))
    assert(
      described.contains("shrunk:   100 (7 steps, stopped by the limit: may not be a minimum)"),
      described
    )
end PropAssertionsSpec
