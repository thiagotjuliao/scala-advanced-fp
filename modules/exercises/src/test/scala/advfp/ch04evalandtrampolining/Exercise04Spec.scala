package advfp.ch04evalandtrampolining

import advfp.core.CourseSuite

import Exercise04.{*, given}

class Exercise04Spec extends CourseSuite:

  private type Counter[A] = State[Int, A]

  // Chapter 03's State overflowed at ten thousand steps; this is a hundred
  // times that.
  private val deep = 1_000_000

  private val starts: List[Int] = List(0, 1, -7, 42)

  private def assertSame[A](x: Counter[A], y: Counter[A]): Unit =
    forAllSamples(starts): s =>
      assertEquals(x.run(s).value, y.run(s).value)

  // Programs that read the state, change it, or both — a law checked only on
  // programs that leave the state alone cannot tell a correct `flatMap` from
  // one that drops the new state.
  private def programs: List[Counter[Int]] =
    List(
      Monad[Counter].pure(5),
      State.get[Int],
      State.modify[Int](_ + 1).map(_ => 0),
      State(s => Eval.now((s * 2, s + 3)))
    )

  private def steps: List[Int => Counter[Int]] =
    List(
      n => Monad[Counter].pure(n + 1),
      n => State.set(n).map(_ => n * 10),
      n => State(s => Eval.later((s - n, s)))
    )

  test("get returns the state and leaves it as it is") {
    assertEquals(State.get[Int].run(3).value, (3, 3))
  }

  test("set replaces the state") {
    assertEquals(State.set(9).run(3).value, (9, ()))
  }

  test("modify transforms the state") {
    assertEquals(State.modify[Int](_ * 10).run(3).value, (30, ()))
  }

  test("pure returns its value and leaves the state as it is") {
    assertEquals(Monad[Counter].pure("x").run(3).value, (3, "x"))
  }

  test("flatMap hands the next step the state the first one left") {
    val program = State.modify[Int](_ + 1).flatMap(_ => State.get[Int])
    assertEquals(program.run(0).value, (1, 1))
  }

  test("flatMap hands the next step the value the first one produced") {
    val program = State.get[Int].flatMap(n => State.set(n * 2).map(_ => n))
    assertEquals(program.run(4).value, (8, 4))
  }

  test("left identity") {
    forAllSamples(steps): f =>
      assertSame(Monad[Counter].pure(3).flatMap(f), f(3))
  }

  test("right identity") {
    forAllSamples(programs): p =>
      assertSame(p.flatMap(Monad[Counter].pure), p)
  }

  test("associativity") {
    forAllSamples(for p <- programs; f <- steps; g <- steps yield (p, f, g)): (p, f, g) =>
      assertSame(p.flatMap(f).flatMap(g), p.flatMap(a => f(a).flatMap(g)))
  }

  // --- stack safety ------------------------------------------------------------

  test("a million left-nested flatMaps run") {
    // The program that overflowed chapter 03: `foldLeft` over `flatMap`.
    val program = (1 to deep).foldLeft(State.get[Int]) { (acc, _) =>
      acc.flatMap(_ => State.modify[Int](_ + 1)).flatMap(_ => State.get[Int])
    }
    assertEquals(program.run(0).value, (deep, deep))
  }

  test("a million right-nested flatMaps run") {
    def countDown(n: Int): Counter[Unit] =
      if n == 0 then Monad[Counter].pure(())
      else State.modify[Int](_ + 1).flatMap(_ => countDown(n - 1))
    assertEquals(countDown(deep).run(0).value, (deep, ()))
  }

  // --- forEach -----------------------------------------------------------------

  test("forEach of no elements changes nothing") {
    assertEquals(forEach(List.empty[Int])(n => State.modify[Int](_ + n)).run(7).value, (7, ()))
  }

  test("forEach runs the steps in order") {
    // Pushing onto a list shows the order: the last element ends up in front.
    val program = forEach(List(1, 2, 3))(n => State.modify[List[Int]](n :: _))
    assertEquals(program.run(Nil).value, (List(3, 2, 1), ()))
  }

  test("forEach sums a million numbers into the state") {
    val numbers = List.range(1L, deep.toLong + 1)
    val program = forEach(numbers)(n => State.modify[Long](_ + n))
    assertEquals(program.run(0L).value, (numbers.sum, ()))
  }
end Exercise04Spec
