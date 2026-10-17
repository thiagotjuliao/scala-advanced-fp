package advfp.ch04evalandtrampolining

import advfp.core.CourseSuite

import java.util.concurrent.atomic.AtomicInteger

import Exercise02.{*, given}

class Exercise02Spec extends CourseSuite:

  private val deep = 1_000_000

  // Whether a thunk ran, and how often, is the claim under test, and only a
  // side effect can observe it. A counter per test, and nothing else mutable.
  private def counted[A](a: A): (AtomicInteger, () => A) =
    val calls = AtomicInteger(0)
    (
      calls,
      () =>
        calls.incrementAndGet(); a
    )

  // Samples of every constructor, so a law cannot pass on `now` alone.
  private def programs: List[Eval[Int]] =
    List(
      Eval.now(5),
      Eval.later(6),
      Eval.always(7),
      Eval.defer(Eval.now(8)),
      Eval.now(2).flatMap(n => Eval.later(n * 10))
    )

  private def steps: List[Int => Eval[Int]] =
    List(
      n => Eval.now(n + 1),
      n => Eval.always(n * 3),
      n => Eval.defer(Eval.later(n - 4))
    )

  test("each constructor's value is its argument") {
    assertEquals(Eval.now(1).value, 1)
    assertEquals(Eval.later(2).value, 2)
    assertEquals(Eval.always(3).value, 3)
    assertEquals(Eval.defer(Eval.now(4)).value, 4)
  }

  // --- when a value is computed ------------------------------------------------

  test("now computes its argument once, before any value is asked for") {
    val (calls, thunk) = counted(1)
    val eval = Eval.now(thunk())
    assertEquals(calls.get, 1)
    assertEquals(eval.value + eval.value, 2)
    assertEquals(calls.get, 1)
  }

  test("later computes nothing until asked, and then only once") {
    val (calls, thunk) = counted(1)
    val eval = Eval.later(thunk())
    assertEquals(calls.get, 0)
    assertEquals(eval.value + eval.value, 2)
    assertEquals(calls.get, 1)
  }

  test("always computes nothing until asked, and then on every value") {
    val (calls, thunk) = counted(1)
    val eval = Eval.always(thunk())
    assertEquals(calls.get, 0)
    assertEquals(eval.value + eval.value, 2)
    assertEquals(calls.get, 2)
  }

  test("defer does not build its Eval until asked") {
    val (calls, thunk) = counted(Eval.now(1))
    val eval = Eval.defer(thunk())
    assertEquals(calls.get, 0)
    assertEquals(eval.value, 1)
    assertEquals(calls.get, 1)
  }

  test("map and flatMap run nothing until asked") {
    val (calls, thunk) = counted(1)
    val eval = Eval.now(0).map(_ => thunk()).flatMap(n => Eval.now(n + 1))
    assertEquals(calls.get, 0)
    assertEquals(eval.value, 2)
    assertEquals(calls.get, 1)
  }

  test("a later inside a chain is still computed once") {
    val (calls, thunk) = counted(10)
    val shared = Eval.later(thunk())
    val both = shared.flatMap(a => shared.map(b => a + b))
    assertEquals(both.value, 20)
    assertEquals(calls.get, 1)
  }

  // --- the laws ----------------------------------------------------------------

  test("pure is now") {
    assertEquals(Monad[Eval].pure("x").value, "x")
  }

  test("left identity") {
    forAllSamples(steps): f =>
      assertEquals(Monad[Eval].pure(3).flatMap(f).value, f(3).value)
  }

  test("right identity") {
    forAllSamples(programs): p =>
      assertEquals(p.flatMap(Monad[Eval].pure).value, p.value)
  }

  test("associativity") {
    forAllSamples(for p <- programs; f <- steps; g <- steps yield (p, f, g)): (p, f, g) =>
      assertEquals(p.flatMap(f).flatMap(g).value, p.flatMap(a => f(a).flatMap(g)).value)
  }

  // --- stack safety ------------------------------------------------------------

  test("a million left-nested flatMaps run") {
    val program = (1 to deep).foldLeft(Eval.now(0))((acc, _) => acc.flatMap(n => Eval.now(n + 1)))
    assertEquals(program.value, deep)
  }

  test("a million maps run") {
    val program = (1 to deep).foldLeft(Eval.later(0))((acc, _) => acc.map(_ + 1))
    assertEquals(program.value, deep)
  }

  test("a million right-nested flatMaps run") {
    def countUp(n: Int): Eval[Int] =
      if n == 0 then Eval.now(0) else Eval.now(()).flatMap(_ => countUp(n - 1).map(_ + 1))
    assertEquals(countUp(deep).value, deep)
  }

  test("a million nested defers run") {
    def countDown(n: Int): Eval[Int] =
      if n == 0 then Eval.now(0) else Eval.defer(countDown(n - 1))
    assertEquals(countDown(deep).value, 0)
  }

  test("a million defers with a map each run") {
    def length(n: Int): Eval[Int] =
      if n == 0 then Eval.now(0) else Eval.defer(length(n - 1)).map(_ + 1)
    assertEquals(length(deep).value, deep)
  }
end Exercise02Spec
