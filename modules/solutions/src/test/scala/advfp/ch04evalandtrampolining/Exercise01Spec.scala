package advfp.ch04evalandtrampolining

import advfp.core.CourseSuite

import Exercise01.{*, given}
import Exercise01.Trampoline.*

class Exercise01Spec extends CourseSuite:

  // Deep enough to overflow any loop that spends a stack frame per step: the
  // naive versions overflow at a few thousand.
  private val deep = 1_000_000

  private def pure[A](a: A): Trampoline[A] = Monad[Trampoline].pure(a)

  // Two trampolines are equal when they run to the same value. The samples
  // mix the three shapes, so a law cannot pass by only ever seeing `Done`.
  private def programs: List[Trampoline[Int]] =
    List(
      pure(5),
      More(() => Done(7)),
      FlatMap(More(() => Done(2)), (n: Int) => Done(n * 10))
    )

  private def steps: List[Int => Trampoline[Int]] =
    List(
      n => pure(n + 1),
      n => More(() => Done(n * 3)),
      n => FlatMap(Done(n), (m: Int) => More(() => Done(m - 4)))
    )

  test("run of Done is its value") {
    assertEquals(Done(3).run, 3)
  }

  test("run of More takes the step") {
    assertEquals(More(() => More(() => Done("x"))).run, "x")
  }

  test("run of FlatMap feeds the source's value to the function") {
    assertEquals(FlatMap(Done(4), (n: Int) => Done(n + 1)).run, 5)
  }

  test("a FlatMap whose source is a FlatMap runs the inner one first") {
    val program = FlatMap(FlatMap(Done(1), (n: Int) => Done(n + 1)), (n: Int) => Done(n * 10))
    assertEquals(program.run, 20)
  }

  test("pure is Done") {
    assertEquals(pure(9).run, 9)
  }

  test("left identity") {
    forAllSamples(steps): f =>
      assertEquals(pure(3).flatMap(f).run, f(3).run)
  }

  test("right identity") {
    forAllSamples(programs): p =>
      assertEquals(p.flatMap(pure).run, p.run)
  }

  test("associativity") {
    forAllSamples(for p <- programs; f <- steps; g <- steps yield (p, f, g)): (p, f, g) =>
      assertEquals(p.flatMap(f).flatMap(g).run, p.flatMap(a => f(a).flatMap(g)).run)
  }

  // --- stack safety ------------------------------------------------------------

  test("a million left-nested flatMaps run") {
    // What `foldLeft` builds: each flatMap wraps all the earlier ones.
    val program = (1 to deep).foldLeft(pure(0))((acc, _) => acc.flatMap(n => pure(n + 1)))
    assertEquals(program.run, deep)
  }

  test("a million right-nested flatMaps run") {
    def countUp(n: Int): Trampoline[Int] =
      if n == 0 then pure(0) else pure(()).flatMap(_ => countUp(n - 1).map(_ + 1))
    assertEquals(countUp(deep).run, deep)
  }

  // --- isEven and isOdd --------------------------------------------------------

  test("zero is even and not odd") {
    assertEquals(isEven(0).run, true)
    assertEquals(isOdd(0).run, false)
  }

  test("isEven and isOdd answer small numbers") {
    forAllSamples(List(1, 2, 3, 10, 11)): n =>
      assertEquals(isEven(n).run, n % 2 == 0)
      assertEquals(isOdd(n).run, n % 2 == 1)
  }

  test("isEven and isOdd answer a million and one") {
    assertEquals(isEven(deep).run, true)
    assertEquals(isOdd(deep + 1).run, true)
    assertEquals(isEven(deep + 1).run, false)
  }
end Exercise01Spec
