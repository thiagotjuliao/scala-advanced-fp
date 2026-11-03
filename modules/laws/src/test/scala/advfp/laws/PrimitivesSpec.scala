package advfp.laws

import scala.annotation.tailrec
import munit.FunSuite

object PrimitivesSpec:
  val five: Size = Size.of(5)
  val seeds: List[Seed] = List.tabulate(20)(i => Seed(i.toLong))

  /** Every value in `tree` down to `depth` levels, every candidate of each. */
  def values[A](tree: Tree[A], depth: Int): List[A] =
    if depth == 0 then List(tree.value)
    else tree.value :: tree.shrinks.toList.flatMap(values(_, depth - 1))

  /** What the runner will do: descend into the first candidate that still
    * fails, until none does.
    */
  @tailrec
  def minimise[A](tree: Tree[A], fails: A => Boolean): A =
    tree.shrinks.find(t => fails(t.value)) match
      case Some(smaller) => minimise(smaller, fails)
      case None => tree.value
end PrimitivesSpec

class PrimitivesSpec extends FunSuite:
  import GenSpec.{distance, histogram, tolerance}
  import PrimitivesSpec.*

  /** Ranges with 0 inside, at an end, and outside on either side, and the
    * ranges at the edges of `Int`, where a distance overflows an `Int`.
    */
  private val ranges: List[(Int, Int)] = List(
    (10, 20),
    (-20, -10),
    (-1000, 1000),
    (-5, 5),
    (0, 9),
    (-9, 0),
    (7, 7),
    (Int.MinValue, Int.MaxValue),
    (Int.MinValue, Int.MinValue + 3),
    (Int.MaxValue - 3, Int.MaxValue),
    (Int.MinValue, -1),
    (0, Int.MaxValue)
  )

  private def target(lo: Int, hi: Int): Int =
    if lo <= 0 && 0 <= hi then 0 else if hi < 0 then hi else lo

  test("const is a leaf, whatever the seed and size"):
    seeds.foreach: seed =>
      val tree = Gen.const("a").run(seed, Size.of(seed.next._1.toInt.abs % 30))
      assertEquals(tree.value, "a")
      assert(tree.shrinks.isEmpty)

  test("choose rejects an empty range when called, not when run"):
    val error = intercept[IllegalArgumentException](Gen.choose(5, 1))
    assert(error.getMessage.contains("5") && error.getMessage.contains("1"), error.getMessage)

  test("choose stays in range: every value, and every candidate two levels down"):
    for (lo, hi) <- ranges; seed <- seeds do
      val outside =
        values(Gen.choose(lo, hi).run(seed, five), depth = 2).filter(v => v < lo || v > hi)
      assertEquals(outside, Nil, s"choose($lo, $hi) at $seed")

  test("choose's first candidate is the bound nearest to 0, or 0 inside the range"):
    for (lo, hi) <- ranges; seed <- seeds do
      val tree = Gen.choose(lo, hi).run(seed, five)
      val expected = target(lo, hi)
      if tree.value == expected then
        assert(tree.shrinks.isEmpty, s"choose($lo, $hi) shrinks its target")
      else assertEquals(tree.shrinks.head.value, expected, s"choose($lo, $hi) from ${tree.value}")

  test("choose's candidates come closer to the target, the last one step away"):
    for (lo, hi) <- ranges; seed <- seeds do
      val tree = Gen.choose(lo, hi).run(seed, five)
      val t = target(lo, hi).toLong
      val gaps = tree.shrinks.map(c => (c.value - t).abs).toList
      if gaps.nonEmpty then
        val clue = s"choose($lo, $hi) from ${tree.value}: $gaps"
        assert(gaps.zip(gaps.tail).forall(_ < _), clue)
        assertEquals((tree.value - t).abs - gaps.last, 1L, clue)

  test("choose shrinks to the edge of a failure, from either side of 0"):
    def edge(lo: Int, hi: Int, fails: Int => Boolean): Int =
      val tree = seeds.map(Gen.choose(lo, hi).run(_, five)).find(t => fails(t.value)).get
      minimise(tree, fails)
    assertEquals(edge(0, 1000, _ >= 100), 100)
    assertEquals(edge(-1000, 0, _ <= -100), -100)
    assertEquals(edge(-1000, 1000, _.abs >= 50).abs, 50)
    assertEquals(edge(10, 1000, _ >= 500), 500)
    assertEquals(edge(Int.MinValue, Int.MaxValue, _ >= 1_000_000), 1_000_000)

  test("choose(n, n) is a leaf"):
    seeds.foreach: seed =>
      val tree = Gen.choose(7, 7).run(seed, five)
      assertEquals((tree.value, tree.shrinks.isEmpty), (7, true))

  test("choose is uniform over its range"):
    val expected = (0 to 3).map(_ -> 0.25).toMap
    val measured = histogram(Gen.choose(0, 3))
    assert(distance(measured, expected) <= tolerance, measured.toString)
    val shifted = histogram(Gen.choose(-12, -9))
    assert(distance(shifted, expected.map((k, p) => (k - 12) -> p)) <= tolerance, shifted.toString)

  test("choose draws from the seed's numbers, not its state: a seed and the next are unrelated"):
    // A seed and the one after it differ by a constant in their state. A choose
    // that read the state would tie each value to the next, and still be
    // uniform over Seed(0) to Seed(4999).
    val quarter = Gen.choose(0, 3)
    val pairs = Gen((seed, size) =>
      val (_, next) = seed.next
      Tree((quarter.run(seed, size).value, quarter.run(next, size).value), LazyList.empty)
    )
    val uniform = (for a <- 0 to 3; b <- 0 to 3 yield (a, b) -> 1.0 / 16).toMap
    val d = distance(histogram(pairs), uniform)
    assert(d <= tolerance, s"distance $d from uniform")

  test("choose ignores the size, and the same seed gives the same tree"):
    for (lo, hi) <- ranges; seed <- seeds.take(5) do
      val tree = values(Gen.choose(lo, hi).run(seed, Size.of(0)), depth = 2)
      assertEquals(values(Gen.choose(lo, hi).run(seed, Size.of(30)), depth = 2), tree)

  test("bool is uniform"):
    val measured = histogram(Gen.bool)
    assert(distance(measured, Map(false -> 0.5, true -> 0.5)) <= tolerance, measured.toString)

  test("bool shrinks true to false, and false to nothing"):
    assertEquals(seeds.map(Gen.bool.run(_, five).value).toSet, Set(false, true))
    seeds.foreach: seed =>
      val tree = Gen.bool.run(seed, five)
      val candidates = tree.shrinks.map(c => (c.value, c.shrinks.isEmpty)).toList
      assertEquals(candidates, if tree.value then List((false, true)) else Nil, s"at $seed")

  test("bool ignores the size, and the same seed gives the same tree"):
    seeds.foreach: seed =>
      val tree = values(Gen.bool.run(seed, Size.of(0)), depth = 2)
      assertEquals(values(Gen.bool.run(seed, Size.of(30)), depth = 2), tree)

  private val digits = Gen.oneOf(Gen.const(0), Gen.const(1), Gen.const(2))

  test("oneOf without alternatives does not compile"):
    assertNoDiff(compileErrors("Gen.oneOf(Gen.const(1))"), "")
    val errors = compileErrors("Gen.oneOf[Int]()")
    assert(errors.contains("missing argument"), errors)

  test("oneOf is uniform over its alternatives, not over their values"):
    val measured = histogram(digits)
    assert(distance(measured, (0 to 2).map(_ -> 1.0 / 3).toMap) <= tolerance, measured.toString)
    val uneven = histogram(Gen.oneOf(Gen.const(0), Gen.choose(1, 3)))
    val expected = Map(0 -> 0.5, 1 -> 1.0 / 6, 2 -> 1.0 / 6, 3 -> 1.0 / 6)
    assert(distance(uneven, expected) <= tolerance, uneven.toString)

  test("oneOf draws the index and the alternative from independent numbers"):
    // choose(0, 1) and choose(0, 3) on the same seed share its low bit, so an
    // index drawn from the alternative's seed would tie the two together.
    val tagged = Gen.oneOf(Gen.choose(0, 3).map((0, _)), Gen.choose(0, 3).map((1, _)))
    val uniform = (for i <- 0 to 1; v <- 0 to 3 yield (i, v) -> 1.0 / 8).toMap
    val d = distance(histogram(tagged), uniform)
    assert(d <= tolerance, s"distance $d from uniform")

  test("oneOf of a single alternative is that alternative"):
    seeds.foreach: seed =>
      val tree = Gen.oneOf(Gen.const("a")).run(seed, five)
      assertEquals((tree.value, tree.shrinks.isEmpty), ("a", true))

  test("oneOf's values all come from an alternative, two levels down"):
    val gen = Gen.oneOf(Gen.choose(10, 20), Gen.choose(100, 200))
    seeds.foreach: seed =>
      val stray = values(gen.run(seed, five), depth = 2)
        .filterNot(v => (10 <= v && v <= 20) || (100 <= v && v <= 200))
      assertEquals(stray, Nil, s"at $seed")

  test("oneOf shrinks to earlier alternatives, the first one first"):
    assertEquals(seeds.map(digits.run(_, five).value).toSet, Set(0, 1, 2))
    seeds.foreach: seed =>
      val tree = digits.run(seed, five)
      val candidates = tree.shrinks.map(_.value).toList
      val clue = s"from ${tree.value}: $candidates"
      if tree.value == 0 then assertEquals(candidates, Nil, clue)
      else
        assertEquals(candidates.headOption, Some(0), clue)
        assert(candidates.forall(_ < tree.value), clue)

  test("oneOf shrinks within the chosen alternative once the earlier ones pass"):
    val gen = Gen.oneOf(Gen.const(-1), Gen.choose(0, 1000))
    val tree = seeds.map(gen.run(_, five)).find(_.value >= 100).get
    assertEquals(tree.shrinks.head.value, -1)
    assertEquals(minimise(tree, _ >= 100), 100)
    assertEquals(minimise(tree, _ => true), -1)

  test("oneOf: the same seed and size give the same tree"):
    val gen = Gen.oneOf(Gen.choose(10, 20), Gen.choose(100, 200))
    seeds.foreach: seed =>
      assertEquals(values(gen.run(seed, five), depth = 2), values(gen.run(seed, five), depth = 2))

  /** Enough seeds to meet an alternative drawn an eighth of the time. */
  private val many = GenSpec.seeds.take(200)

  test("frequency rejects a weight of 0 or less, and weights past Int.MaxValue, when called"):
    intercept[IllegalArgumentException](Gen.frequency((0, Gen.const(0))))
    intercept[IllegalArgumentException](Gen.frequency((1, Gen.const(0)), (-1, Gen.const(1))))
    intercept[IllegalArgumentException](
      Gen.frequency((Int.MaxValue, Gen.const(0)), (1, Gen.const(1)))
    )
    Gen.frequency((Int.MaxValue, Gen.const(0)))
    Gen.frequency((Int.MaxValue - 1, Gen.const(0)), (1, Gen.const(1)))

  test("frequency without alternatives does not compile"):
    assertNoDiff(compileErrors("Gen.frequency((1, Gen.const(1)))"), "")
    val errors = compileErrors("Gen.frequency[Int]()")
    assert(errors.contains("missing argument"), errors)

  private val weighted =
    Gen.frequency((1, Gen.const(0)), (2, Gen.const(1)), (5, Gen.const(2)))

  test("frequency draws each alternative in proportion to its weight"):
    val measured = histogram(weighted)
    val expected = Map(0 -> 1.0 / 8, 1 -> 2.0 / 8, 2 -> 5.0 / 8)
    assert(distance(measured, expected) <= tolerance, measured.toString)
    // Equal weights show a cumulative sum off by one, which 1, 2, 5 hide.
    val even = histogram(Gen.frequency((1, Gen.const(0)), (1, Gen.const(1))))
    assert(distance(even, Map(0 -> 0.5, 1 -> 0.5)) <= tolerance, even.toString)

  test("frequency draws the index and the alternative from independent numbers"):
    val tagged = Gen.frequency(
      (1, Gen.choose(0, 3).map((0, _))),
      (1, Gen.choose(0, 3).map((1, _)))
    )
    val uniform = (for i <- 0 to 1; v <- 0 to 3 yield (i, v) -> 1.0 / 8).toMap
    val d = distance(histogram(tagged), uniform)
    assert(d <= tolerance, s"distance $d from uniform")

  test("frequency of a single alternative is that alternative"):
    seeds.foreach: seed =>
      val tree = Gen.frequency((3, Gen.const("a"))).run(seed, five)
      assertEquals((tree.value, tree.shrinks.isEmpty), ("a", true))

  test("frequency's values all come from an alternative, two levels down"):
    val gen = Gen.frequency((1, Gen.choose(10, 20)), (3, Gen.choose(100, 200)))
    seeds.foreach: seed =>
      val stray = values(gen.run(seed, five), depth = 2)
        .filterNot(v => (10 <= v && v <= 20) || (100 <= v && v <= 200))
      assertEquals(stray, Nil, s"at $seed")

  test("frequency shrinks to earlier alternatives, the first one first"):
    assertEquals(many.map(weighted.run(_, five).value).toSet, Set(0, 1, 2))
    many.foreach: seed =>
      val tree = weighted.run(seed, five)
      val candidates = tree.shrinks.map(_.value).toList
      val clue = s"from ${tree.value}: $candidates"
      if tree.value == 0 then assertEquals(candidates, Nil, clue)
      else
        assertEquals(candidates.headOption, Some(0), clue)
        assert(candidates.forall(_ < tree.value), clue)

  test("frequency shrinks within the chosen alternative once the earlier ones pass"):
    val gen = Gen.frequency((1, Gen.const(-1)), (3, Gen.choose(0, 1000)))
    val tree = seeds.map(gen.run(_, five)).find(_.value >= 100).get
    assertEquals(tree.shrinks.head.value, -1)
    assertEquals(minimise(tree, _ >= 100), 100)
    assertEquals(minimise(tree, _ => true), -1)

  test("option is None a quarter of the time"):
    val measured = histogram(Gen.option(Gen.choose(0, 2)))
    val expected = Map(None -> 0.25, Some(0) -> 0.25, Some(1) -> 0.25, Some(2) -> 0.25)
    assert(distance(measured, expected) <= tolerance, measured.toString)

  test("option shrinks Some to None first, then within the value; None does not shrink"):
    many.foreach: seed =>
      val tree = Gen.option(Gen.choose(0, 1000)).run(seed, five)
      val candidates = tree.shrinks.map(_.value).toList
      val clue = s"from ${tree.value}: $candidates"
      tree.value match
        case None => assertEquals(candidates, Nil, clue)
        case Some(v) =>
          assertEquals(candidates.headOption, Some(None), clue)
          assert(candidates.tail.forall(_.exists(_ < v)), clue)

  test("option shrinks to the edge of a failure inside Some"):
    val tree =
      seeds.map(Gen.option(Gen.choose(0, 1000)).run(_, five)).find(_.value.exists(_ >= 100)).get
    assertEquals(minimise(tree, _.exists(_ >= 100)), Some(100))

  private val sides = Gen.either(Gen.choose(10, 20), Gen.choose(100, 200))

  test("either is Left half the time"):
    val measured = histogram(Gen.either(Gen.const("l"), Gen.const(1)))
    assert(
      distance(measured, Map(Left("l") -> 0.5, Right(1) -> 0.5)) <= tolerance,
      measured.toString
    )

  test("either shrinks Right to a Left first, then within the Right; a Left stays a Left"):
    many.foreach: seed =>
      val tree = sides.run(seed, five)
      val candidates = tree.shrinks.map(_.value).toList
      val clue = s"from ${tree.value}: $candidates"
      if tree.value.isLeft then assert(candidates.forall(_.isLeft), clue)
      else
        assert(candidates.headOption.exists(_.isLeft), clue)
        assert(candidates.tail.forall(_.isRight), clue)

  test("either's values come from their side's generator, two levels down"):
    seeds.foreach: seed =>
      val stray = values(sides.run(seed, five), depth = 2).filter:
        case Left(l) => l < 10 || l > 20
        case Right(r) => r < 100 || r > 200
      assertEquals(stray, Nil, s"at $seed")

  test("either shrinks to the edge of a failure inside Right"):
    val tree = seeds.map(sides.run(_, five)).find(_.value.exists(_ >= 150)).get
    assertEquals(minimise(tree, _.exists(_ >= 150)), Right(150))

  test("pairs draws its two sides from independent numbers, even from one generator"):
    val quarter = Gen.choose(0, 3)
    val uniform = (for a <- 0 to 3; b <- 0 to 3 yield (a, b) -> 1.0 / 16).toMap
    val d = distance(histogram(Gen.pairs(quarter, quarter)), uniform)
    assert(d <= tolerance, s"distance $d from uniform")

  /** Every candidate of a pair of values from `three`, where `v` shrinks to
    * `0 until v`: the first side's, then the second's.
    */
  private def bothSides(a: Int, b: Int): List[(Int, Int)] =
    (0 until a).map((_, b)).toList ++ (0 until b).map((a, _))

  test("pairs shrinks the first side first, then the second"):
    val tree = Gen.pairs(GenSpec.three, GenSpec.three).run(seeds.head, five)
    assertEquals(tree.value, (3, 3))
    assertEquals(tree.shrinks.map(_.value).toList, bothSides(3, 3))

  test("pairs' candidates keep the second side's tree; once it shrinks, the first is fixed"):
    // The candidates of the second side are the trees of `b` alone (Tree's
    // flatMap), so the first side no longer shrinks under them.
    val candidates = Gen.pairs(GenSpec.three, GenSpec.three).run(seeds.head, five).shrinks.toList
    val (first, second) = candidates.splitAt(3)
    first.foreach: candidate =>
      val (a, b) = candidate.value
      assertEquals(candidate.shrinks.map(_.value).toList, bothSides(a, b), s"from ($a, $b)")
    second.foreach: candidate =>
      val (a, b) = candidate.value
      assertEquals(candidate.shrinks.map(_.value).toList, (0 until b).map((a, _)).toList)

  test("pairs' values come from their side's generator, two levels down"):
    val gen = Gen.pairs(Gen.choose(10, 20), Gen.choose(100, 200))
    seeds.foreach: seed =>
      val stray = values(gen.run(seed, five), depth = 2).filterNot: (a, b) =>
        10 <= a && a <= 20 && 100 <= b && b <= 200
      assertEquals(stray, Nil, s"at $seed")

  test("frequency, option, either and pairs: the same seed and size give the same tree"):
    val pair = Gen.pairs(Gen.choose(0, 1000), Gen.choose(0, 1000))
    val option = Gen.option(Gen.choose(0, 1000))
    seeds.foreach: seed =>
      assertEquals(values(weighted.run(seed, five), 2), values(weighted.run(seed, five), 2))
      assertEquals(values(option.run(seed, five), 2), values(option.run(seed, five), 2))
      assertEquals(values(sides.run(seed, five), 2), values(sides.run(seed, five), 2))
      assertEquals(values(pair.run(seed, five), 2), values(pair.run(seed, five), 2))
end PrimitivesSpec
