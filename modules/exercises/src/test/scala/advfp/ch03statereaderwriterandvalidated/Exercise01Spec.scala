package advfp.ch03statereaderwriterandvalidated

import advfp.core.CourseSuite

import Exercise01.{*, given}
import Exercise01.Instr.*

class Exercise01Spec extends CourseSuite:

  private type Counter[A] = State[Int, A]

  // Two programs are equal when they agree on every starting state. The
  // samples include zero and a negative, where an off-by-one in the threading
  // tends to show.
  private val starts: List[Int] = List(0, 1, -7, 42)

  private def assertSame[A](x: Counter[A], y: Counter[A]): Unit =
    forAllSamples(starts): s =>
      assertEquals(x.run(s), y.run(s))

  // Programs that read the state, change it, or both — a law checked only on
  // programs that leave the state alone cannot tell a correct `flatMap` from
  // one that drops the new state.
  private def programs: List[Counter[Int]] =
    List(
      Monad[Counter].pure(5),
      State.get[Int],
      State.modify[Int](_ + 1).map(_ => 0),
      State(s => (s * 2, s + 3))
    )

  private def steps: List[Int => Counter[Int]] =
    List(
      n => Monad[Counter].pure(n + 1),
      n => State.set(n).map(_ => n * 10),
      n => State(s => (s - n, s))
    )

  test("get returns the state and leaves it as it is") {
    assertEquals(State.get[Int].run(3), (3, 3))
  }

  test("set replaces the state") {
    assertEquals(State.set(9).run(3), (9, ()))
  }

  test("modify transforms the state") {
    assertEquals(State.modify[Int](_ * 10).run(3), (30, ()))
  }

  test("pure returns its value and leaves the state as it is") {
    assertEquals(Monad[Counter].pure("x").run(3), (3, "x"))
  }

  test("flatMap hands the next step the state the first one left") {
    val program = State.modify[Int](_ + 1).flatMap(_ => State.get[Int])
    assertEquals(program.run(0), (1, 1))
  }

  test("flatMap hands the next step the value the first one produced") {
    val program = State.get[Int].flatMap(n => State.set(n * 2).map(_ => n))
    assertEquals(program.run(4), (8, 4))
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

  // --- the stack machine -------------------------------------------------------

  test("Push puts a number on top") {
    assertEquals(step(Push(4)).run(List(1)), (List(4, 1), ()))
  }

  test("Add and Mul replace the top two with the result") {
    assertEquals(step(Add).run(List(2, 3, 9)), (List(5, 9), ()))
    assertEquals(step(Mul).run(List(2, 3, 9)), (List(6, 9), ()))
  }

  test("Dup copies the top, Swap exchanges the top two, Pop drops the top") {
    assertEquals(step(Dup).run(List(7, 1)), (List(7, 7, 1), ()))
    assertEquals(step(Swap).run(List(7, 1, 2)), (List(1, 7, 2), ()))
    assertEquals(step(Pop).run(List(7, 1)), (List(1), ()))
  }

  test("an instruction that finds too few numbers leaves the stack as it is") {
    forAllSamples(List((Add, List(1)), (Mul, Nil), (Dup, Nil), (Swap, List(3)), (Pop, Nil))):
      (instr, stack) => assertEquals(step(instr).run(stack), (stack, ()))
  }

  test("execute runs the instructions in order") {
    // (2 + 3) * 4
    val program = List(Push(2), Push(3), Add, Push(4), Mul)
    assertEquals(execute(program).run(Nil), (List(20), ()))
  }

  test("execute runs order-sensitive instructions in order") {
    // Swap then Pop leaves the 2; Pop then Swap would leave the 1.
    assertEquals(execute(List(Push(1), Push(2), Swap, Pop)).run(Nil), (List(2), ()))
  }

  test("execute of no instructions changes nothing") {
    assertEquals(execute(Nil).run(List(5, 6)), (List(5, 6), ()))
  }
end Exercise01Spec
