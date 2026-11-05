package advfp.laws

import advfp.typeclasses.*
import munit.FunSuite

class SuiteSpec extends FunSuite with PropAssertions:
  import Fixtures.*
  import PropAssertions.failures

  private val seeds = List.tabulate(5)(i => Config(seed = i.toLong))
  private val ints = Gen.choose(-100, 100)
  private val lists: [X] => Gen[X] => Gen[List[X]] = [X] => (g: Gen[X]) => Gen.listOf(g)
  private val options: [X] => Gen[X] => Gen[Option[X]] = [X] => (g: Gen[X]) => Gen.option(g)
  private val eithers: [X] => Gen[X] => Gen[OrInt[X]] =
    [X] => (g: Gen[X]) => Gen.either(Gen.choose(-10, 10), g)

  private val semigroupLaws = List("Semigroup: associativity")
  private val monoidLaws =
    semigroupLaws ++ List("Monoid: left identity", "Monoid: right identity")
  private val functorLaws = List("Functor: identity", "Functor: composition")
  private val applicativeLaws = functorLaws ++ List(
    "Applicative: identity",
    "Applicative: homomorphism",
    "Applicative: interchange",
    "Applicative: composition",
    "Applicative: map agrees with pure(f).ap"
  )
  private val monadLaws = applicativeLaws ++ List(
    "Monad: left identity",
    "Monad: right identity",
    "Monad: associativity",
    "Monad: ap agrees with flatMap"
  )

  private def names(suite: Suite): List[String] = suite.all.map(_._1)

  /** The laws `suite` breaks on any of the seeds, in the order `all` has them. */
  private def broken(suite: Suite): List[String] =
    names(suite).filter(law => seeds.exists(failures(suite, _).exists(_._1 == law)))

  /** What the failing `law` reports, one per seed that finds it. */
  private def reports(suite: Suite, law: String): List[Report[?]] =
    seeds.flatMap(failures(suite, _).collect { case (`law`, report) => report })

  /** The inputs of a report, whichever way a suite nests them in tuples. */
  private def parts(input: Any): List[Any] =
    input match
      case tuple: Tuple => tuple.productIterator.toList.flatMap(parts)
      case other => List(other)

  private def sides(report: Report[?]): Set[String] =
    report.failure match
      case Failure.Unequal(lhs, rhs) => Set(lhs, rhs)
      case Failure.Threw(error) => fail(s"threw $error")

  private def lengths(report: Report[?]): List[Int] =
    parts(report.shrunk).collect:
      case xs: List[?] => xs.size
      case zip: ZipList[?] => zip.prefix.size

  test("all is a suite's own laws when it has no parents, named with the suite"):
    val prop = Prop.forAll(Gen.const(1))(_ => true)
    val suite = Suite("S", Nil, List("a" -> prop, "b" -> prop))
    assertEquals(names(suite), List("S: a", "S: b"))

  test("all gives the parents' laws first, and a shared ancestor's only once"):
    val prop = Prop.forAll(Gen.const(1))(_ => true)
    val root = Suite("R", Nil, List("r" -> prop))
    val left = Suite("L", List(root), List("l" -> prop))
    val right = Suite("T", List(root), List("t" -> prop))
    val top = Suite("D", List(left, right), List("d" -> prop))
    assertEquals(names(top), List("R: r", "L: l", "T: t", "D: d"))

  test("all tells laws apart by name, since two builds of one law are different values"):
    val top = Suite("D", List(Suite.semigroup(ints), Suite.semigroup(ints)), Nil)
    assertEquals(names(top), semigroupLaws)

  test("each suite has the laws of the design, its parents' first"):
    assertEquals(names(Suite.semigroup(ints)), semigroupLaws)
    assertEquals(names(Suite.monoid(ints)), monoidLaws)
    assertEquals(names(Suite.functor[List](lists)), functorLaws)
    assertEquals(names(Suite.applicative[List](lists)), applicativeLaws)
    assertEquals(names(Suite.monad[List](lists)), monadLaws)

  test("the lawful applicatives pass, where the order of effects shows"):
    seeds.foreach: config =>
      assertSuite(Suite.applicative[Option](options), config)
      assertSuite(Suite.applicative[List](lists), config)
      assertSuite(Suite.applicative[OrInt](eithers), config)

  test("the lawful instances pass, on the suite of everything they are"):
    seeds.foreach: config =>
      assertSuite(Suite.semigroup(ints), config)
      assertSuite(Suite.monoid(ints), config)
      assertSuite(Suite.monoid(Gen.listOf(ints)), config)
      assertSuite(Suite.monoid(Gen.option(Gen.listOf(ints))), config)
      assertSuite(Suite.monad[Option](options), config)
      assertSuite(Suite.monad[List](lists), config)
      assertSuite(Suite.monad[OrInt](eithers), config)

  test("subtraction breaks associativity, at a = 0, b = 0, c = ±1"):
    given Semigroup[Int] = subtraction
    val suite = Suite.semigroup(ints)
    assertEquals(broken(suite), semigroupLaws)
    reports(suite, "Semigroup: associativity").foreach: report =>
      assertEquals(sides(report), Set("1", "-1"), report)
      assertEquals(parts(report.shrunk).count(_ == 0), 2, report)

  test("* with empty = 0 breaks both identities, and not associativity, at a = ±1"):
    given Monoid[Int] = timesWithZero
    val suite = Suite.monoid(ints)
    assertEquals(broken(suite), List("Monoid: left identity", "Monoid: right identity"))
    reports(suite, "Monoid: left identity").foreach: report =>
      assert(sides(report) == Set("0", "1") || sides(report) == Set("0", "-1"), report)

  test("an empty that is an identity on the left only breaks only right identity"):
    given Monoid[Int] = keepsTheRight
    assertEquals(broken(Suite.monoid(ints)), List("Monoid: right identity"))

  test("an empty that is an identity on the right only breaks only left identity"):
    given Monoid[Int] = keepsTheLeft
    assertEquals(broken(Suite.monoid(ints)), List("Monoid: left identity"))

  test("a functor that reverses breaks identity, on two distinct elements, one of them 0"):
    given Functor[List] = reversing
    val suite = Suite.functor[List](lists)
    assertEquals(broken(suite), functorLaws)
    reports(suite, "Functor: identity").foreach: report =>
      parts(report.shrunk) match
        case List(xs: List[?]) =>
          assertEquals(xs.size, 2, report)
          assertEquals(xs.distinct.size, 2, report)
          assert(xs.contains(0), report)
        case other => fail(s"not one list: $other")

  test("an Either whose ap keeps the value's Left breaks only ap's agreement with flatMap"):
    given Monad[OrInt] = keepsTheValuesLeft
    val suite = Suite.monad[OrInt](eithers)
    assertEquals(broken(suite), List("Monad: ap agrees with flatMap"))
    reports(suite, "Monad: ap agrees with flatMap").foreach: report =>
      assert(
        sides(report) == Set("Left(0)", "Left(1)") || sides(report) == Set("Left(0)", "Left(-1)"),
        report
      )

  test("a List whose ap swaps the loops breaks only ap's agreement with flatMap"):
    given Monad[List] = swapsTheLoops
    val suite = Suite.monad[List](lists)
    assertEquals(broken(suite), List("Monad: ap agrees with flatMap"))
    reports(suite, "Monad: ap agrees with flatMap").foreach: report =>
      assertEquals(lengths(report), List(2, 2), report)

  test("a List ap that uses only the first function is caught by interchange"):
    given Applicative[List] = usesTheFirstFunction
    assert(broken(Suite.applicative[List](lists)).contains("Applicative: interchange"))

  test("a List ap that reverses the functions is caught by interchange and composition"):
    given Applicative[List] = reversesTheFunctions
    val found = broken(Suite.applicative[List](lists))
    assert(found.contains("Applicative: interchange"), found)
    assert(found.contains("Applicative: composition"), found)

  test("a pure with two elements breaks homomorphism"):
    given Applicative[List] = pureTwice
    val found = broken(Suite.applicative[List](lists))
    assert(found.contains("Applicative: homomorphism"), found)

  test("a map that disagrees with pure and ap breaks their agreement"):
    given Applicative[List] = mapReverses
    val found = broken(Suite.applicative[List](lists))
    assert(found.contains("Applicative: map agrees with pure(f).ap"), found)

  test("a flatMap that truncates f's result on pure(a) breaks left identity, not right"):
    given Monad[List] = firstResultOfOne
    val found = broken(Suite.monad[List](lists))
    assert(found.contains("Monad: left identity"), found)
    assert(!found.contains("Monad: right identity"), found)

  test("a flatMap that drops all but the first element breaks right identity, not left"):
    given Monad[List] = dropsTheRest
    val found = broken(Suite.monad[List](lists))
    assert(found.contains("Monad: right identity"), found)
    assert(!found.contains("Monad: left identity"), found)

  test("a ZipList with a one-element pure breaks identity, on two elements"):
    given Applicative[ZipList] = onePure
    val suite = Suite.applicative[ZipList](zipLists)
    assert(broken(suite).contains("Applicative: identity"), broken(suite))
    reports(suite, "Applicative: identity").foreach: report =>
      assertEquals(lengths(report), List(2), report)

  // Not the design's `m` of two elements: greedy shrinking stops between 2 and
  // 11 (docs/kernel/m01-the-law-checker.md#acceptance). What holds on every
  // seed is what the failure is, a side that ends one element early.
  test("a ZipList with diagonal as flatMap breaks only associativity, a side ending early"):
    given Monad[ZipList] = diagonal
    val suite = Suite.monad[ZipList](zipLists)
    assertEquals(broken(suite), List("Monad: associativity"))
    reports(suite, "Monad: associativity").foreach: report =>
      assert(lengths(report).forall(_ >= 2), report)
      report.failure match
        case Failure.Unequal(lhs, rhs) => assertEquals(lhs, rhs.dropRight(1) + ", 0)", report)
        case Failure.Threw(error) => fail(s"threw $error")
end SuiteSpec
