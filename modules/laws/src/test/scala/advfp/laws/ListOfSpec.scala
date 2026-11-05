package advfp.laws

import munit.FunSuite

class ListOfSpec extends FunSuite:
  import GenSpec.{distance, histogram, leaf, three, tolerance}
  import PrimitivesSpec.{five, minimise, seeds, values}

  private val many = GenSpec.seeds.take(200)
  private val thirty = Size.of(30)

  /** A different number for every seed, which never shrinks: in a list of
    * these, every candidate is a removal, and every removal can be told apart.
    */
  private val distinct: Gen[Long] = Gen((seed, _) => leaf(seed.next._1))

  /** The removals the design document lays out: blocks of `n`, `n / 2`, ...,
    * 1 elements, each block from left to right, the last one shorter when
    * the length is not a multiple.
    */
  private def removals[A](xs: List[A]): List[List[A]] =
    LazyList
      .iterate(xs.length)(_ / 2)
      .takeWhile(_ > 0)
      .toList
      .flatMap: k =>
        val blocks = xs.grouped(k).toList
        blocks.indices.map(i => blocks.patch(i, Nil, 1).flatten)

  /** Every candidate of a list of values from `three`, where `v` shrinks to
    * `0 until v`: the removals, then each element through its candidates.
    */
  private def candidates(xs: List[Int]): List[List[Int]] =
    removals(xs) ++ (for i <- xs.indices.toList; c <- 0 until xs(i) yield xs.updated(i, c))

  test("listOf's length is uniform from 0 to the size"):
    val measured = histogram(Gen.listOf(Gen.const(0)).map(_.length))
    val expected = (0 to 5).map(_ -> 1.0 / 6).toMap
    assert(distance(measured, expected) <= tolerance, measured.toString)

  test("listOf never goes past the size, and at size 0 is the empty list"):
    for n <- 0 to 30; seed <- seeds do
      val length = Gen.listOf(Gen.const(0)).run(seed, Size.of(n)).value.length
      assert(length <= n, s"length $length at size $n")
    seeds.foreach: seed =>
      val tree = Gen.listOf(Gen.choose(0, 9)).run(seed, Size.of(0))
      assertEquals((tree.value, tree.shrinks.isEmpty), (Nil, true))

  test("listOf draws each element from a seed of its own"):
    val pairs = GenSpec.seeds
      .map(Gen.listOf(Gen.choose(0, 3)).run(_, five).value)
      .collect { case a :: b :: _ => (a, b) }
    val measured = pairs.groupMapReduce(identity)(_ => 1.0 / pairs.size)(_ + _)
    val uniform = (for a <- 0 to 3; b <- 0 to 3 yield (a, b) -> 1.0 / 16).toMap
    val d = distance(measured, uniform)
    assert(d <= tolerance, s"distance $d from uniform over ${pairs.size} pairs")

  test("listOf gives each element a seed that no other element draws from"):
    // An element that draws two numbers in a row. Seeds chained by next would
    // hand the second number of one element to the next as its first, which a
    // one-number element never shows.
    val twice: Gen[(Int, Int)] = Gen: (seed, _) =>
      val (a, after) = seed.next
      leaf(((a >>> 62).toInt, (after.next._1 >>> 62).toInt))
    val pairs = GenSpec.seeds
      .map(Gen.listOf(twice).run(_, five).value)
      .collect { case (_, second) :: (first, _) :: _ => (second, first) }
    val measured = pairs.groupMapReduce(identity)(_ => 1.0 / pairs.size)(_ + _)
    val uniform = (for a <- 0 to 3; b <- 0 to 3 yield (a, b) -> 1.0 / 16).toMap
    val d = distance(measured, uniform)
    assert(d <= tolerance, s"distance $d from uniform over ${pairs.size} pairs")

  test("listOf draws its length from a seed that no element starts from"):
    // Without a split, the length's number is the state of the first
    // element's seed, so an element that reads its seed's state is tied to the
    // length; one that draws from the seed sees the tie only through a mix.
    val state: Gen[Int] = Gen((seed, _) => leaf(Math.floorMod(seed.state, 6L).toInt))
    val pairs = GenSpec.seeds
      .map(Gen.listOf(state).run(_, five).value)
      .collect { case xs @ first :: _ => (xs.length, first) }
    val measured = pairs.groupMapReduce(identity)(_ => 1.0 / pairs.size)(_ + _)
    val uniform = (for n <- 1 to 5; v <- 0 to 5 yield (n, v) -> 1.0 / 30).toMap
    // Thirty cells over about 4200 pairs: twice the usual tolerance. The tie
    // measured 0.83 on 2026-11-03.
    val d = distance(measured, uniform)
    assert(d <= 2 * tolerance, s"distance $d from uniform over ${pairs.size} pairs")

  test("listOf removes the whole list first, then halves, then single elements"):
    val tree = many.map(Gen.listOf(distinct).run(_, five)).find(_.value.length == 4).get
    val xs = tree.value
    val (a, b, c, d) = (xs(0), xs(1), xs(2), xs(3))
    val expected =
      List(Nil, List(c, d), List(a, b), List(b, c, d), List(a, c, d), List(a, b, d), List(a, b, c))
    assertEquals(tree.shrinks.map(_.value).toList, expected)

  test("listOf's removals follow the blocks at every length up to 30"):
    val trees = many.map(Gen.listOf(distinct).run(_, thirty))
    assert(trees.map(_.value.length).toSet.size > 20, "too few lengths met")
    trees.foreach: tree =>
      assertEquals(
        tree.shrinks.map(_.value).toList,
        removals(tree.value),
        s"length ${tree.value.length}"
      )

  test(
    "listOf shrinks the elements after the removals, left to right, through their own candidates"
  ):
    seeds.foreach: seed =>
      val tree = Gen.listOf(three).run(seed, five)
      assertEquals(tree.shrinks.map(_.value).toList, candidates(tree.value), s"at $seed")

  test("listOf's candidates shrink by the same rule, so a removal keeps its elements' trees"):
    seeds.foreach: seed =>
      Gen
        .listOf(three)
        .run(seed, five)
        .shrinks
        .foreach: candidate =>
          assertEquals(
            candidate.shrinks.map(_.value).toList,
            candidates(candidate.value),
            s"at $seed"
          )

  test("listOf's elements come from the element generator, two levels down"):
    seeds.foreach: seed =>
      val stray = values(Gen.listOf(Gen.choose(10, 20)).run(seed, five), depth = 2).flatten
        .filter(v => v < 10 || v > 20)
      assertEquals(stray, Nil, s"at $seed")

  test("listOf shrinks to the edge of a failure: the shortest list, the smallest elements"):
    val numbers = Gen.listOf(Gen.choose(0, 100))
    val long = many.map(numbers.run(_, thirty)).find(_.value.length >= 3).get
    assertEquals(minimise(long, _.length >= 3), List(0, 0, 0))
    val large = many.map(numbers.run(_, thirty)).find(_.value.sum >= 10).get
    assertEquals(minimise(large, _.sum >= 10).sum, 10)

  test("listOf: the same seed and size give the same tree"):
    seeds.foreach: seed =>
      val gen = Gen.listOf(Gen.choose(0, 9))
      assertEquals(values(gen.run(seed, five), depth = 2), values(gen.run(seed, five), depth = 2))
end ListOfSpec
