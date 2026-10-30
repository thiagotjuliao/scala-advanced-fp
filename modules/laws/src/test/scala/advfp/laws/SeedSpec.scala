package advfp.laws

import java.util.SplittableRandom
import munit.{FunSuite, Location}

/** `Seed` against its oracle, `java.util.SplittableRandom` (ADR 0008).
  *
  * The oracle is mutable: every `nextLong` and `split` advances it. That is
  * the JDK's design, not this suite's, and each oracle lives inside one test.
  */
class SeedSpec extends FunSuite:

  private val roots: List[Long] = List(0L, 1L, -1L, 42L, Long.MinValue, Long.MaxValue)

  private def draw(seed: Seed, n: Int): List[Long] =
    LazyList.unfold(seed)(s => Some(s.next)).take(n).toList

  private def drawJdk(oracle: SplittableRandom, n: Int): List[Long] =
    List.fill(n)(oracle.nextLong())

  test("next reproduces SplittableRandom's nextLong from every root"):
    roots.foreach: n =>
      assertEquals(draw(Seed(n), 1000), drawJdk(new SplittableRandom(n), 1000), s"root $n")

  /** Splits `depth` levels deep, then compares a few numbers of every child
    * and every advanced parent at the leaves. A child's stream uses its own
    * gamma, so this is where a `next` that loses the gamma shows itself.
    */
  private def assertSameTree(seed: Seed, oracle: SplittableRandom, depth: Int, path: String)(using
      Location
  ): Unit =
    val (child, parent) = seed.split
    val oracleChild = oracle.split()
    if depth > 0 then
      assertSameTree(child, oracleChild, depth - 1, s"$path/child")
      assertSameTree(parent, oracle, depth - 1, s"$path/parent")
    else
      assertEquals(draw(child, 5), drawJdk(oracleChild, 5), s"child at $path")
      assertEquals(draw(parent, 5), drawJdk(oracle, 5), s"parent at $path")

  test("split reproduces SplittableRandom's split, four levels deep"):
    roots.foreach: n =>
      assertSameTree(Seed(n), new SplittableRandom(n), 3, s"root $n")

  test("a seed is a value: drawing from it twice gives the same number"):
    val seed = Seed(7L)
    assertEquals(seed.next, seed.next)
    assertEquals(seed.split, seed.split)

  test("next advances: the following number comes from the returned seed"):
    val (first, rest) = Seed(7L).next
    assertNotEquals(rest.next._1, first)

  test("a child, its parent and the original seed are three different seeds"):
    val seed = Seed(7L)
    val (child, parent) = seed.split
    assertNotEquals(child, parent)
    assertNotEquals(child, seed)
    assertNotEquals(parent, seed)
    assertNotEquals(draw(child, 10), draw(parent, 10))

  test("every gamma made by split is odd"):
    def gammas(seed: Seed, depth: Int): List[Long] =
      if depth == 0 then List(seed.gamma)
      else
        val (child, parent) = seed.split
        seed.gamma :: gammas(child, depth - 1) ++ gammas(parent, depth - 1)
    roots.foreach: n =>
      gammas(Seed(n), 6).foreach(g => assert((g & 1L) == 1L, s"even gamma $g under root $n"))

  test("a seed can only be made by Seed(n) and split"):
    assert(compileErrors("Seed(1L, 2L)").nonEmpty, "the two-field constructor is public")
    assert(compileErrors("Seed(1L).copy(gamma = 2L)").nonEmpty, "copy can set an even gamma")
end SeedSpec
