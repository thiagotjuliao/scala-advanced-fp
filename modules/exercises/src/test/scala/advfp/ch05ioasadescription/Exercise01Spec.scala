package advfp.ch05ioasadescription

import advfp.core.CourseSuite

import java.util.concurrent.atomic.AtomicInteger

import Exercise01.*
import Exercise01.IO.*

class Exercise01Spec extends CourseSuite:

  // Deep enough to overflow any loop that spends a stack frame per node.
  private val deep = 1_000_000

  // Whether an effect ran, and how often, is the claim under test, and only a
  // side effect can observe it. A counter per test, and nothing else mutable.
  private def counter(): AtomicInteger = AtomicInteger(0)

  private def tick(calls: AtomicInteger): IO[Int] = Delay(() => calls.incrementAndGet())

  private final class Boom(label: String) extends RuntimeException(label)

  private val boom = Boom("boom")

  // --- the leaves --------------------------------------------------------------

  test("Pure runs to its value") {
    assertEquals(Pure(3).unsafeRun(), Right(3))
  }

  test("RaiseError runs to its error") {
    assertEquals(RaiseError(boom).unsafeRun(), Left(boom))
  }

  test("Delay runs its thunk when run, not when built, and on every run") {
    val calls = counter()
    val io = tick(calls)
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right(1))
    assertEquals(io.unsafeRun(), Right(2))
  }

  test("a Delay whose thunk throws runs to that error") {
    assertEquals(Delay(() => throw boom).unsafeRun(), Left(boom))
  }

  // --- flatMap and map ---------------------------------------------------------

  test("flatMap feeds the value to the function and runs what it returns") {
    assertEquals(Pure(4).flatMap(n => Pure(n + 1)).unsafeRun(), Right(5))
  }

  test("map transforms the value") {
    assertEquals(Pure(4).map(_ * 10).unsafeRun(), Right(40))
  }

  test("flatMap and map run nothing until the program runs") {
    val calls = counter()
    val io = tick(calls).flatMap(n => tick(calls).map(_ + n))
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right(3))
  }

  test("an error skips the functions after it") {
    val calls = counter()
    val io = RaiseError(boom).flatMap(_ => tick(calls)).map(_ + 1)
    assertEquals(io.unsafeRun(), Left(boom))
    assertEquals(calls.get, 0)
  }

  test("a function that throws is the program's error") {
    assertEquals(Pure(1).flatMap(_ => throw boom).unsafeRun(), Left(boom))
    assertEquals(Pure(1).map(_ => throw boom).unsafeRun(), Left(boom))
  }

  // --- handleErrorWith ---------------------------------------------------------

  test("handleErrorWith recovers from an error") {
    val io = RaiseError(boom).handleErrorWith(e => Pure(e.getMessage))
    assertEquals(io.unsafeRun(), Right("boom"))
  }

  test("handleErrorWith leaves a success alone, and never calls the handler") {
    val calls = counter()
    val io = Pure(1).handleErrorWith(_ => tick(calls))
    assertEquals(io.unsafeRun(), Right(1))
    assertEquals(calls.get, 0)
  }

  test("a handler catches an error raised deep inside its program") {
    val io = Pure(1)
      .flatMap(_ => Pure(2).flatMap(_ => RaiseError(boom)))
      .handleErrorWith(_ => Pure(0))
    assertEquals(io.unsafeRun(), Right(0))
  }

  test("a handler does not catch an error raised after it") {
    // The handler's program succeeded, so the handler is gone by the time the
    // later flatMap fails: the error reaches the top.
    val calls = counter()
    val io = Pure(1).handleErrorWith(_ => tick(calls)).flatMap(_ => RaiseError(boom))
    assertEquals(io.unsafeRun(), Left(boom))
    assertEquals(calls.get, 0)
  }

  test("after a recovery the program goes on with what follows") {
    val io = RaiseError(boom).handleErrorWith(_ => Pure(1)).flatMap(n => Pure(n + 1))
    assertEquals(io.unsafeRun(), Right(2))
  }

  test("a handler that fails hands its own error to the next handler out") {
    val other = Boom("other")
    val io = RaiseError(boom)
      .handleErrorWith(_ => RaiseError(other))
      .handleErrorWith(e => Pure(e.getMessage))
    assertEquals(io.unsafeRun(), Right("other"))
  }

  test("a handler that throws is the program's error") {
    val other = Boom("other")
    assertEquals(RaiseError(boom).handleErrorWith(_ => throw other).unsafeRun(), Left(other))
  }

  test("a handler catches an exception thrown inside its program") {
    // A thunk that throws as the first step under a handler: a loop that pops
    // the handler before running the thunk lets the error past it.
    assertEquals(Delay(() => throw boom).handleErrorWith(_ => Pure(0)).unsafeRun(), Right(0))
    assertEquals(
      Pure(1).flatMap(_ => throw boom).handleErrorWith(_ => Pure(0)).unsafeRun(),
      Right(0)
    )
  }

  test("a program can be run twice, and does its effects twice") {
    val calls = counter()
    val io = tick(calls).flatMap(_ => RaiseError(boom)).handleErrorWith(_ => tick(calls))
    assertEquals(io.unsafeRun(), Right(2))
    assertEquals(io.unsafeRun(), Right(4))
  }

  // --- stack safety ------------------------------------------------------------

  test("a million left-nested flatMaps run") {
    // What `foldLeft` builds: each flatMap wraps all the earlier ones.
    val io = (1 to deep).foldLeft(Pure(0): IO[Int])((acc, _) => acc.flatMap(n => Pure(n + 1)))
    assertEquals(io.unsafeRun(), Right(deep))
  }

  test("a million right-nested flatMaps run") {
    def countUp(n: Int): IO[Int] =
      if n == 0 then Pure(0) else Pure(()).flatMap(_ => countUp(n - 1).map(_ + 1))
    assertEquals(countUp(deep).unsafeRun(), Right(deep))
  }

  test("a million nested handlers run, on success and on error") {
    def nest(io: IO[Int]): IO[Int] =
      (1 to deep).foldLeft(io)((acc, _) => acc.handleErrorWith(e => RaiseError(e)))
    assertEquals(nest(Pure(7)).unsafeRun(), Right(7))
    assertEquals(nest(RaiseError(boom)).unsafeRun(), Left(boom))
  }

  test("an error a million flatMaps deep reaches the handler at the top") {
    val calls = counter()
    val io = (1 to deep).foldLeft(RaiseError(boom): IO[Int]) { (acc, _) =>
      acc.flatMap(_ => tick(calls))
    }
    assertEquals(io.handleErrorWith(_ => Pure(-1)).unsafeRun(), Right(-1))
    assertEquals(calls.get, 0)
  }

  test("a million errors raised and recovered in a row run") {
    val io = (1 to deep).foldLeft(Pure(0): IO[Int]) { (acc, _) =>
      acc.flatMap(n => RaiseError(boom).handleErrorWith(_ => Pure(n + 1)))
    }
    assertEquals(io.unsafeRun(), Right(deep))
  }
end Exercise01Spec
