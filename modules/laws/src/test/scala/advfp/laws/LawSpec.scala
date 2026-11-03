package advfp.laws

import advfp.typeclasses.{Eq, Semigroup}
import advfp.typeclasses.Semigroup.given
import munit.FunSuite

class LawSpec extends FunSuite:

  /** Coarser than `==`, so a law that holds under it and not under `==` shows
    * which of the two `holds` asked.
    */
  private val parity: Eq[Int] = (x, y) => Math.floorMod(x, 2) == Math.floorMod(y, 2)

  test("a law holds when its sides are equal, and not when they differ"):
    assert(Law(1, 1).holds)
    assert(Law(List(1, 2), List(1, 2)).holds)
    assert(!Law(1, 2).holds)
    assert(!Law(List(1, 2), List(2, 1)).holds)

  test("holds compares with the Eq it is given, not with =="):
    assert(Law(1, 3).holds(using parity), "1 and 3 have the same parity")
    assert(!Law(1, 2).holds(using parity), "1 and 2 do not")

  test("a law keeps its sides as given, left on the left"):
    val law = Law(1, 2)
    assertEquals((law.lhs, law.rhs), (1, 2))

  test("<-> builds the law with its left operand as lhs"):
    assertEquals(1 <-> 2, Law(1, 2))
    assertEquals(List(1) <-> Nil, Law(List(1), Nil))

  test("<-> binds tighter than ==, so a law reads as the equation it checks"):
    assert((Option(1).map(_ + 1) <-> Option(2)).holds)
    assertEquals(1 + 1 <-> 2, Law(2, 2))

  test("<-> binds looser than |+| only with parentheses: | is the weakest operator"):
    assert((((1 |+| 2) |+| 3) <-> (1 |+| (2 |+| 3))).holds)
    assert((Semigroup[Int].combine(1, 2) <-> 3).holds)
    val unparenthesised = compileErrors("(1 |+| 2) |+| 3 <-> 1 |+| (2 |+| 3)")
    // Read as (1 |+| 2) |+| (3 <-> 1) |+| (2 |+| 3), so the error is a Law
    // where |+| wants an Int, not anything else that could fail to compile.
    assert(unparenthesised.contains("Found:    advfp.laws.Law[Int]"), unparenthesised)
    assert(unparenthesised.contains("Required: Int"), unparenthesised)
end LawSpec
