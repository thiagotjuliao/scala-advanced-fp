package advfp.ch05ioasadescription

import advfp.core.CourseSuite

import java.util.concurrent.atomic.AtomicInteger

import Exercise03.*

class Exercise03Spec extends CourseSuite:

  private val deep = 1_000_000

  // Counters observe how many times a program ran. One per test.
  private def counter(): AtomicInteger = AtomicInteger(0)

  // A program that returns 1, 2, 3, ... on successive runs: the effect under
  // test is "how many times did this run".
  private def next(calls: AtomicInteger): IO[Int] = IO.delay(calls.incrementAndGet())

  private final class Boom(val run: Int) extends RuntimeException(s"run $run failed")

  // Fails on its first `failures` runs, each time with an error naming the run,
  // and then succeeds with the number of the run that succeeded.
  private def flaky(calls: AtomicInteger, failures: Int): IO[Int] =
    next(calls).flatMap(n => if n <= failures then IO.raiseError(Boom(n)) else IO.pure(n))

  // --- twice -------------------------------------------------------------------

  test("twice runs the program twice, in order") {
    val calls = counter()
    assertEquals(twice(next(calls)).unsafeRun(), Right((1, 2)))
  }

  test("twice of a named program still runs it twice") {
    // The refactoring that changes an impure program changes nothing here.
    val calls = counter()
    val program = next(calls)
    assertEquals(twice(program).unsafeRun(), Right((1, 2)))
  }

  test("twice runs nothing until the program runs") {
    val calls = counter()
    val io = twice(next(calls))
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right((1, 2)))
  }

  test("twice fails if the first run fails, without a second run") {
    val calls = counter()
    assertEquals(twice(flaky(calls, 1)).unsafeRun().left.map(_.getMessage), Left("run 1 failed"))
    assertEquals(calls.get, 1)
  }

  // --- retry -------------------------------------------------------------------

  test("retry of one attempt is the program itself") {
    val calls = counter()
    assertEquals(retry(flaky(calls, 1), 1).unsafeRun().left.map(_.getMessage), Left("run 1 failed"))
    assertEquals(calls.get, 1)
  }

  test("retry runs the program again until it succeeds") {
    val calls = counter()
    assertEquals(retry(flaky(calls, 2), 3).unsafeRun(), Right(3))
    assertEquals(calls.get, 3)
  }

  test("retry stops at the first success") {
    val calls = counter()
    assertEquals(retry(flaky(calls, 1), 5).unsafeRun(), Right(2))
    assertEquals(calls.get, 2)
  }

  test("retry gives up after the last attempt, with the last error") {
    val calls = counter()
    assertEquals(retry(flaky(calls, 5), 3).unsafeRun().left.map(_.getMessage), Left("run 3 failed"))
    assertEquals(calls.get, 3)
  }

  test("retry runs nothing until the program runs") {
    val calls = counter()
    val io = retry(flaky(calls, 1), 2)
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right(2))
  }

  test("retry rejects fewer than one attempt") {
    val calls = counter()
    intercept[IllegalArgumentException](retry(next(calls), 0))
  }

  test("retry runs a million attempts") {
    val calls = counter()
    assertEquals(retry(flaky(calls, deep - 1), deep).unsafeRun(), Right(deep))
    assertEquals(calls.get, deep)
  }

  // --- repeatUntil -------------------------------------------------------------

  test("repeatUntil returns the first value that satisfies the predicate") {
    val calls = counter()
    assertEquals(repeatUntil(next(calls))(_ % 4 == 0).unsafeRun(), Right(4))
    assertEquals(calls.get, 4)
  }

  test("repeatUntil runs the program once when the first value will do") {
    val calls = counter()
    assertEquals(repeatUntil(next(calls))(_ => true).unsafeRun(), Right(1))
    assertEquals(calls.get, 1)
  }

  test("repeatUntil runs nothing until the program runs") {
    val calls = counter()
    val io = repeatUntil(next(calls))(_ >= 2)
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right(2))
  }

  test("repeatUntil stops at an error") {
    val calls = counter()
    val io = repeatUntil(flaky(calls, 1))(_ => true)
    assertEquals(io.unsafeRun().left.map(_.getMessage), Left("run 1 failed"))
    assertEquals(calls.get, 1)
  }

  test("repeatUntil runs a million times") {
    val calls = counter()
    assertEquals(repeatUntil(next(calls))(_ == deep).unsafeRun(), Right(deep))
  }
end Exercise03Spec
