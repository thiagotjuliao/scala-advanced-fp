package advfp.laws

import scala.annotation.tailrec
import munit.FunSuite

class PrimitivesSpec extends FunSuite:
  import GenSpec.{distance, histogram, tolerance}

  private val five = Size.of(5)
  private val seeds: List[Seed] = List.tabulate(20)(i => Seed(i.toLong))

  /** Every value in `tree` down to `depth` levels, every candidate of each. */
  private def values[A](tree: Tree[A], depth: Int): List[A] =
    if depth == 0 then List(tree.value)
    else tree.value :: tree.shrinks.toList.flatMap(values(_, depth - 1))

  /** What the runner will do: descend into the first candidate that still
    * fails, until none does.
    */
  @tailrec
  private def minimise[A](tree: Tree[A], fails: A => Boolean): A =
    tree.shrinks.find(t => fails(t.value)) match
      case Some(smaller) => minimise(smaller, fails)
      case None => tree.value

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
end PrimitivesSpec
