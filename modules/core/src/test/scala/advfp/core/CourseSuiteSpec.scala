package advfp.core

/** Tests the test helpers.
  *
  * These assertions are the instrument every other chapter is measured with,
  * and an instrument that silently passes everything would make the whole
  * repository look solved. The fixtures below are deliberately trivial and
  * deliberately include the failing direction.
  */
class CourseSuiteSpec extends CourseSuite:

  test("a true claim about types compiles") {
    assertTypeChecks("summon[Int =:= Int]")
    assertTypeChecks("summon[List[Int] <:< Seq[Int]]")
  }

  test("a false claim about types is rejected") {
    assertTypeError("summon[Int =:= String]")
    assertTypeError("summon[Seq[Int] <:< List[Int]]")
  }

  test("calling a stub fails with Unsolved") {
    def stub: Int = unsolved
    intercept[Unsolved](stub)
  }

  test("a property that holds for every sample passes") {
    forAllSamples(List(0, 1, -1, Int.MaxValue))(n => assertEquals(n + 0, n))
  }

  test("a property that fails names the sample") {
    val failure = intercept[munit.FailException](
      forAllSamples(List(1, 2, 3))(n => assert(n < 3))
    )
    assert(failure.getMessage.contains("does not hold for 3"), failure.getMessage)
  }

  test("a property checked against no samples is refused") {
    intercept[AssertionError](forAllSamples(List.empty[Int])(_ => ()))
  }

  test("a rejection can be pinned to its reason") {
    assertTypeErrorContains("val n: Int = \"not a number\"", "Found:")
  }

  // `rejectedAtTheCallSite` fails where it is called, so the echoed source line
  // is the assertion's own line, which spells out the fragment.
  test("a reason only the echoed source line mentions is the wrong reason") {
    intercept[munit.FailException](
      assertTypeErrorContains("rejectedAtTheCallSite()", "a reason nobody gave")
    )
  }

  test("a rejection can be pinned to how its message opens") {
    assertTypeErrorStartsWith("val n: Int = \"not a number\"", "Found:")
  }

  test("text further into the message does not open it") {
    intercept[munit.FailException](
      assertTypeErrorStartsWith("val n: Int = \"not a number\"", "Required:")
    )
  }
end CourseSuiteSpec

inline def rejectedAtTheCallSite(): Int = scala.compiletime.error("rejected")
