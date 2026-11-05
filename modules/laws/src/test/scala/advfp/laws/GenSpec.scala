package advfp.laws

import advfp.typeclasses.{Applicative, Functor, Monad}
import munit.{FunSuite, Location}

/** Generators built by hand from `Seed`: the primitives are not written yet,
  * and the suite must not lean on them.
  */
object GenSpec:
  def leaf[A](a: A): Tree[A] = Tree(a, LazyList.empty)

  /** 0 to 3, uniformly: the top two bits of the seed's next number. */
  val quarter: Gen[Int] = Gen((seed, _) => leaf((seed.next._1 >>> 62).toInt))

  /** The size it was run with. */
  val size: Gen[Int] = Gen((_, size) => leaf(size.toInt))

  /** 3, shrinking to 0, 1 and 2, the same tree for every seed. */
  val three: Gen[Int] = Gen((_, _) => Tree.unfold(3)(n => LazyList.range(0, n)))

  /** The number of seeds a distribution is measured on, and how far apart two
    * measured distributions may be. Measured in
    * docs/kernel/decisions/0010-gen-is-lawful-in-distribution.md.
    */
  val samples = 5000
  val tolerance = 0.05

  val seeds: List[Seed] = List.tabulate(samples)(i => Seed(i.toLong))

  val five: Size = Size.of(5)

  def histogram[A](gen: Gen[A]): Map[A, Double] =
    seeds
      .groupMapReduce(gen.run(_, five).value)(_ => 1)(_ + _)
      .view
      .mapValues(_.toDouble / samples)
      .toMap

  /** Total variation distance: half the sum of the differences, so 0 for the
    * same distribution and 1 for two with no value in common.
    */
  def distance[A](p: Map[A, Double], q: Map[A, Double]): Double =
    (p.keySet ++ q.keySet).toList.map(k => (p.getOrElse(k, 0.0) - q.getOrElse(k, 0.0)).abs).sum / 2
end GenSpec

class GenSpec extends FunSuite:
  import GenSpec.*

  private val G = Monad[Gen]

  private def roots[A](gen: Gen[A]): List[A] = seeds.map(gen.run(_, five).value)

  private def samePointwise[A](left: Gen[A], right: Gen[A], clue: => String)(using Location): Unit =
    assertEquals(roots(left), roots(right), clue)

  private def sameDistribution[A](left: Gen[A], right: Gen[A], clue: => String)(using
      Location
  ): Unit =
    val d = distance(histogram(left), histogram(right))
    assert(d <= tolerance, s"$clue: distance $d above $tolerance")

  test("a generator is deterministic: the same seed and size give the same tree"):
    val gen = quarter.flatMap(a => quarter.map(_ + 4 * a))
    seeds
      .take(50)
      .foreach: seed =>
        assertEquals(gen.run(seed, five).value, gen.run(seed, five).value)

  test("quarter, the suite's own generator, is uniform"):
    val expected = Map(0 -> 0.25, 1 -> 0.25, 2 -> 0.25, 3 -> 0.25)
    assert(distance(histogram(quarter), expected) <= tolerance, histogram(quarter).toString)

  // map does not split the seed, so the functor laws hold seed by seed.

  test("functor identity, seed by seed"):
    samePointwise(G.map(quarter)(identity), quarter, "map(identity)")

  test("functor composition, seed by seed"):
    val f: Int => Int = _ + 1
    val g: Int => Int = _ * 10
    samePointwise(G.map(G.map(quarter)(f))(g), G.map(quarter)(f andThen g), "composition")

  test("the member methods and the instance are one implementation"):
    val f: Int => Gen[Int] = a => quarter.map(_ + 4 * a)
    samePointwise(quarter.map(_ + 1), G.map(quarter)(_ + 1), "map")
    samePointwise(quarter.flatMap(f), G.flatMap(quarter)(f), "flatMap")

  test("ap agrees with flatMap, seed by seed"):
    val ff: Gen[Int => Int] = quarter.map(q => a => a * 4 + q)
    samePointwise(G.ap(ff)(quarter), G.flatMap(ff)(f => G.map(quarter)(f)), "ap")

  test("pure ignores the seed"):
    assertEquals(roots(G.pure(7)).distinct, List(7))

  // flatMap splits the seed, so the monad laws hold in distribution only
  // (ADR 0010).

  private val f: Int => Gen[Int] = a => quarter.map(q => 4 * a + q)
  private val g: Int => Gen[Int] = b => quarter.map(q => (b + q) % 7)

  test("left identity, in distribution"):
    List(0, 3).foreach: a =>
      sameDistribution(G.flatMap(G.pure(a))(f), f(a), s"at $a")

  test("right identity, in distribution"):
    sameDistribution(G.flatMap(quarter)(G.pure), quarter, "right identity")

  test("associativity, in distribution"):
    sameDistribution(
      G.flatMap(G.flatMap(quarter)(f))(g),
      G.flatMap(quarter)(a => G.flatMap(f(a))(g)),
      "associativity"
    )

  test("the two sides of flatMap are independent: two quarters make sixteen pairs"):
    val pairs = quarter.flatMap(a => quarter.map(b => (a, b)))
    val uniform = (for a <- 0 to 3; b <- 0 to 3 yield (a, b) -> 1.0 / 16).toMap
    val d = distance(histogram(pairs), uniform)
    assert(d <= tolerance, s"pairs are correlated: distance $d from uniform")

  test("both sides of flatMap get the same size"):
    val sizes = size.flatMap(a => size.map(b => (a, b)))
    assertEquals(sizes.run(Seed(1L), Size.of(0)).value, (0, 0))
    assertEquals(sizes.run(Seed(1L), Size.of(17)).value, (17, 17))

  test("shrinking the first value keeps the second side's seed"):
    // Every candidate regenerates the second side from the same seed, so the
    // quarter drawn is the same in every node.
    val gen = three.flatMap(a => quarter.map(q => 10 * a + q))
    seeds
      .take(50)
      .foreach: seed =>
        val tree = gen.run(seed, five)
        val q = tree.value % 10
        assertEquals(tree.value / 10, 3)
        assertEquals(tree.shrinks.map(_.value).toList, List(q, 10 + q, 20 + q), s"at $seed")

  // ADR 0007, "types of our own": one instance, in Gen's companion.

  test("Functor, Applicative and Monad of Gen are the same instance"):
    val instances = List(Functor[Gen], Applicative[Gen], Monad[Gen])
    assert(instances.forall(_ eq G), s"instances differ: $instances")
end GenSpec
