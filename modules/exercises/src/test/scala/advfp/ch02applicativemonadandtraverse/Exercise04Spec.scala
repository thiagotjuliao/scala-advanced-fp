package advfp.ch02applicativemonadandtraverse

import advfp.core.CourseSuite

import Exercise04.{*, given}

class Exercise04Spec extends CourseSuite:

  // `pure` is infinite, so a comparison involving it compares a prefix. Every
  // other list in this spec is finite and is compared whole.
  private def prefix[A](z: ZipList[A]): List[A] = z.values.take(50).toList

  // A finite list longer than this was not meant to be finite.
  private def finite[A](z: ZipList[A]): Boolean = z.values.lengthCompare(1000) < 0

  private val inc: Int => Int = _ + 1
  private val double: Int => Int = _ * 2

  private val values: List[ZipList[Int]] =
    List(ZipList.of(), ZipList.of(1), ZipList.of(1, 2, 3), ZipList.of(4, 5, 6, 7, 8))

  private val functions: List[ZipList[Int => Int]] =
    List(ZipList.of(), ZipList.of(inc), ZipList.of(inc, double, inc))

  test("ap zips the functions with the values, as long as the shorter") {
    val F = Applicative[ZipList]
    assertEquals(F.ap(ZipList.of(inc, double))(ZipList.of(10, 20, 30)), ZipList.of(11, 40))
  }

  test("pure repeats its value without end") {
    assertEquals(prefix(Applicative[ZipList].pure(7)), List.fill(50)(7))
  }

  test("map transforms every element and keeps the length") {
    assertEquals(Applicative[ZipList].map(ZipList.of(1, 2, 3))(double), ZipList.of(2, 4, 6))
  }

  test("identity holds for lists of every length") {
    val F = Applicative[ZipList]
    forAllSamples(values): v =>
      assertEquals(F.ap(F.pure((a: Int) => a))(v), v)
  }

  test("homomorphism") {
    val F = Applicative[ZipList]
    assertEquals(prefix(F.ap(F.pure(inc))(F.pure(1))), prefix(F.pure(2)))
  }

  test("interchange") {
    val F = Applicative[ZipList]
    forAllSamples(functions): u =>
      assertEquals(F.ap(u)(F.pure(5)), F.ap(F.pure((h: Int => Int) => h(5)))(u))
  }

  test("composition") {
    val F = Applicative[ZipList]
    val compose = (p: Int => Int) => (q: Int => Int) => p compose q
    forAllSamples(for u <- functions; v <- functions; w <- values yield (u, v, w)): (u, v, w) =>
      assertEquals(F.ap(F.ap(F.ap(F.pure(compose))(u))(v))(w), F.ap(u)(F.ap(v)(w)))
  }

  test("the diagonal agrees with ap") {
    val F = Applicative[ZipList]
    forAllSamples(for ff <- functions; fa <- values yield (ff, fa)): (ff, fa) =>
      assertEquals(diagonal(ff)(h => F.map(fa)(h)), F.ap(ff)(fa))
  }

  test("the counterexample is finite") {
    assert(finite(m), "m must be finite")
    m.values.foreach: a =>
      assert(finite(f(a)), s"f($a) must be finite")
      f(a).values.foreach(b => assert(finite(g(b)), s"g($b) must be finite"))
  }

  test("the counterexample breaks associativity") {
    val left = diagonal(diagonal(m)(f))(g)
    val right = diagonal(m)(a => diagonal(f(a))(g))
    assertNotEquals(left, right, s"both sides are $left")
  }
end Exercise04Spec
