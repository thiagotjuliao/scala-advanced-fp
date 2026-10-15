package advfp.ch01typeclassesandlaws

import advfp.core.CourseSuite

import Exercise01.*

class Exercise01Spec extends CourseSuite:

  private val ints = List(0, 1, -1, 2, 7, -13)
  private val triples = for a <- ints; b <- ints; c <- ints yield (a, b, c)

  test("Sum is associative") {
    forAllSamples(triples): (a, b, c) =>
      assertEquals(((Sum(a) |+| Sum(b)) |+| Sum(c)).value, (Sum(a) |+| (Sum(b) |+| Sum(c))).value)
  }

  test("Sum's identity is 0, on both sides") {
    assertEquals(Monoid[Sum].empty.value, 0)
    forAllSamples(ints): n =>
      assertEquals((Monoid[Sum].empty |+| Sum(n)).value, n)
      assertEquals((Sum(n) |+| Monoid[Sum].empty).value, n)
  }

  test("Sum adds") {
    assertEquals((Sum(2) |+| Sum(5)).value, 7)
  }

  test("Prod is associative") {
    forAllSamples(triples): (a, b, c) =>
      assertEquals(
        ((Prod(a) |+| Prod(b)) |+| Prod(c)).value,
        (Prod(a) |+| (Prod(b) |+| Prod(c))).value
      )
  }

  test("Prod's identity is 1, on both sides") {
    assertEquals(Monoid[Prod].empty.value, 1)
    forAllSamples(ints): n =>
      assertEquals((Monoid[Prod].empty |+| Prod(n)).value, n)
      assertEquals((Prod(n) |+| Monoid[Prod].empty).value, n)
  }

  test("Prod multiplies") {
    assertEquals((Prod(2) |+| Prod(5)).value, 10)
  }

  test("combineAll folds with the element type's monoid") {
    assertEquals(combineAll(List(Sum(1), Sum(2), Sum(3), Sum(4))).value, 10)
    assertEquals(combineAll(List(Prod(1), Prod(2), Prod(3), Prod(4))).value, 24)
  }

  test("combineAll of nothing is the identity") {
    assertEquals(combineAll(List.empty[Sum]).value, 0)
    assertEquals(combineAll(List.empty[Prod]).value, 1)
  }
end Exercise01Spec
