package advfp.laws

import munit.FunSuite

class CheckSpec extends FunSuite:
  import Prop.forAll
  import Runner.{check, recheck}

  private val fixed = Config(seed = 42)
  private val runSeeds = List.tabulate(20)(_.toLong)

  /** The test's size as its value, with no candidates: a property over it
    * sees the size schedule.
    */
  private val sizes: Gen[Int] = Gen((_, size) => Tree(size.toInt, LazyList.empty))

  private def failed[A](result: Result[A]): Report[A] =
    result match
      case Result.Failed(report) => report
      case Result.Passed(tests) => fail(s"passed $tests tests")

  test("a property that always holds passes every test"):
    assertEquals(check(forAll(Gen.choose(0, 10))(_ => true), fixed), Result.Passed(100))
    assertEquals(
      check(forAll(Gen.choose(0, 10))(_ => true), fixed.copy(tests = 7)),
      Result.Passed(7)
    )

  test("the first test has size 0, and the sizes reach maxSize"):
    val first = failed(check(forAll(sizes)(_ => false), fixed))
    assertEquals((first.test, first.size), (1, Size.of(0)))
    val last = failed(check(forAll(sizes)(_ < 30), fixed))
    assertEquals(last.size, Size.of(30))
    assertEquals(check(forAll(sizes)(_ <= 30), fixed), Result.Passed(100))

  test("test i of n has size i * (maxSize + 1) / n"):
    // The first i, from 0, with i * 31 / 100 >= 30 is 97: test number 98.
    assertEquals(failed(check(forAll(sizes)(_ < 30), fixed)).test, 98)
    // With as many tests as sizes, test i has size i: every size once.
    val one = Config(tests = 31, maxSize = 30, seed = 42)
    (0 to 30).foreach: k =>
      assertEquals(failed(check(forAll(sizes)(_ < k), one)).test, k + 1, s"size $k")

  test("a law that fails reports both sides at the shrunk input"):
    val report = failed(check(forAll(Gen.choose(0, 1000))(x => (x min 100) <-> x), fixed))
    assertEquals(report.shrunk, 101)
    assertEquals(report.failure, Failure.Unequal("100", "101"))

  test("shrinking finds the edge: x < 100 over choose(0, 1000) shrinks to 100"):
    runSeeds.foreach: seed =>
      val report = failed(check(forAll(Gen.choose(0, 1000))(_ < 100), Config(seed = seed)))
      assertEquals(report.shrunk, 100, s"seed $seed")
      assert(report.original >= 100, s"the original ${report.original} passes")
      assert(!report.exhausted, s"seed $seed")

  test("shrinking finds the edge: xs.sum < 10 shrinks to a list whose sum is 10"):
    runSeeds.foreach: seed =>
      val report =
        failed(check(forAll(Gen.listOf(Gen.choose(0, 10)))(_.sum < 10), Config(seed = seed)))
      assertEquals(report.shrunk.sum, 10, s"${report.shrunk} at seed $seed")

  test("shrinks counts the steps taken, and a failure with no candidates takes none"):
    val leaf = Gen((_, _) => Tree(7, LazyList.empty))
    val report = failed(check(forAll(leaf)(_ => false), fixed))
    assertEquals((report.original, report.shrunk, report.shrinks), (7, 7, 0))
    assert(!report.exhausted)
    // 0 to 10, one step each: candidate n + 1 fails while n < 10.
    val chain =
      Gen((_, _) => Tree.unfold(0)(n => if n < 10 then LazyList(n + 1) else LazyList.empty))
    val chained = failed(check(forAll(chain)(_ => false), fixed))
    assertEquals((chained.shrunk, chained.shrinks), (10, 10))

  test("the shrink limit counts candidates tried, not steps taken"):
    // Each even n >= 0 fails, and has -1 (passes) then n + 2 (fails) as
    // candidates: two tried for each step taken, forever.
    def node(n: Int): Tree[Int] =
      Tree(n, Tree(-1, LazyList.empty) #:: node(n + 2) #:: LazyList.empty)
    val ladder = Gen((_, _) => node(0))
    val report = failed(check(forAll(ladder)(_ < 0), fixed.copy(maxShrinks = 10)))
    assertEquals((report.shrunk, report.shrinks), (10, 5))
    assert(report.exhausted)

  test("a node whose candidates all pass, endlessly, does not hang the run"):
    val endless = Gen((_, _) => Tree(-1, LazyList.continually(Tree(1, LazyList.empty))))
    val report = failed(check(forAll(endless)(_ > 0), fixed))
    assertEquals((report.shrunk, report.shrinks), (-1, 0))
    assert(report.exhausted)

  test("with maxShrinks 0 nothing is shrunk"):
    val report = failed(check(forAll(Gen.choose(0, 1000))(_ < 100), fixed.copy(maxShrinks = 0)))
    assertEquals(report.shrunk, report.original)
    assertEquals(report.shrinks, 0)
    assert(report.exhausted)

  test("what a property throws is a failure, and its input is shrunk"):
    // Every x from 500 up fails, so a run is sure to find one: only 500 throws,
    // and it is the edge the shrink has to reach.
    val report =
      failed(check(forAll(Gen.choose(0, 1000))(x => x < 500 || 1000 / (x - 500) < 0), fixed))
    assertEquals(report.shrunk, 500)
    report.failure match
      case Failure.Threw(_: ArithmeticException) => ()
      case other => fail(s"not an ArithmeticException: $other")

  test("any failure counts while shrinking: an unequal input may shrink to one that throws"):
    // 0 to 49 throw, 50 to 99 pass, 100 and up fail as a law.
    val prop = forAll(Gen.choose(0, 1000)): x =>
      if x < 50 then throw IllegalStateException(x.toString) else (x min 99) <-> x
    val report = failed(check(prop, fixed))
    assertEquals(report.shrunk, 0)
    assert(report.failure.isInstanceOf[Failure.Threw], report.failure)

  test("a fatal error is not a counter-example: it escapes check"):
    // Not `intercept`: munit's catches `NonFatal` only, so a fatal error goes
    // through it and aborts the suite, with the tests after it skipped.
    val escaped =
      try
        check(forAll(Gen.choose(0, 10))(_ => throw LinkageError("fatal")), fixed)
        false
      catch case _: LinkageError => true
    assert(escaped, "check swallowed a fatal error")

  test("the same config gives the same result, and another seed another run"):
    val prop = forAll(Gen.choose(0, 1000))(_ < 100)
    assertEquals(check(prop, fixed), check(prop, fixed))
    val reports = runSeeds.map(seed => failed(check(prop, Config(seed = seed))))
    assert(reports.map(r => (r.seed, r.original)).distinct.size > 1, reports)

  test("a report's seed and size rebuild the failing input"):
    runSeeds.foreach: seed =>
      val prop = forAll(Gen.listOf(Gen.choose(0, 10)))(_.sum < 10)
      val report = failed(check(prop, Config(seed = seed)))
      assertEquals(prop.gen.run(Seed(report.seed), report.size).value, report.original)

  test("recheck runs the failing test again, and shrinks it to the same input"):
    runSeeds.foreach: seed =>
      val prop = forAll(Gen.listOf(Gen.choose(0, 10)))(_.sum < 10)
      val report = failed(check(prop, Config(seed = seed)))
      val again = failed(recheck(prop, report.seed, report.size))
      assertEquals((again.original, again.shrunk), (report.original, report.shrunk))
      assertEquals((again.seed, again.size), (report.seed, report.size))

  test("recheck of a test that passes passes one test"):
    assertEquals(recheck(forAll(Gen.choose(0, 10))(_ => true), 7L, Size.of(3)), Result.Passed(1))

  test("a config needs at least one test, a non-negative maxSize and shrink limit"):
    intercept[IllegalArgumentException](Config(tests = 0))
    intercept[IllegalArgumentException](Config(maxShrinks = -1))
    intercept[IllegalArgumentException](Config(maxSize = -1))
    assertEquals(Config(maxShrinks = 0).maxShrinks, 0)
    assertEquals(check(forAll(sizes)(_ == 0), Config(maxSize = 0, seed = 42)), Result.Passed(100))
end CheckSpec
