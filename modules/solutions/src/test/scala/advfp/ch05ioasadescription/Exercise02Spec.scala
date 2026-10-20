package advfp.ch05ioasadescription

import advfp.core.CourseSuite

import java.util.concurrent.atomic.AtomicInteger

import Exercise02.*

class Exercise02Spec extends CourseSuite:

  private val deep = 1_000_000

  // Counters observe whether, and how often, an effect ran. One per test.
  private def counter(): AtomicInteger = AtomicInteger(0)

  private def tick(calls: AtomicInteger): IO[Unit] = IO.delay(calls.incrementAndGet()).map(_ => ())

  private final class Boom(label: String) extends RuntimeException(label)

  private val boom = Boom("boom")
  private val other = Boom("other")

  // --- attempt and rethrow -----------------------------------------------------

  test("attempt turns a success into Right") {
    assertEquals(IO.pure(1).attempt.unsafeRun(), Right(Right(1)))
  }

  test("attempt turns an error into Left, and never fails") {
    assertEquals(IO.raiseError[Int](boom).attempt.unsafeRun(), Right(Left(boom)))
    assertEquals(IO.delay[Int](throw boom).attempt.unsafeRun(), Right(Left(boom)))
  }

  test("attempt runs nothing until the program runs") {
    val calls = counter()
    val io = tick(calls).attempt
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right(Right(())))
    assertEquals(calls.get, 1)
  }

  test("rethrow turns Left into an error and Right into a value") {
    assertEquals(IO.pure[Either[Throwable, Int]](Left(boom)).rethrow.unsafeRun(), Left(boom))
    assertEquals(IO.pure[Either[Throwable, Int]](Right(2)).rethrow.unsafeRun(), Right(2))
  }

  test("rethrow undoes attempt") {
    forAllSamples(List(IO.pure(3), IO.raiseError[Int](boom), IO.delay[Int](throw other))): io =>
      assertEquals(io.attempt.rethrow.unsafeRun(), io.unsafeRun())
  }

  // --- handleError -------------------------------------------------------------

  test("handleError turns an error into a value") {
    assertEquals(IO.raiseError[String](boom).handleError(_.getMessage).unsafeRun(), Right("boom"))
  }

  test("handleError leaves a success alone") {
    assertEquals(IO.pure("ok").handleError(_.getMessage).unsafeRun(), Right("ok"))
  }

  test("a handleError function that throws is the program's error") {
    assertEquals(IO.raiseError[Int](boom).handleError(_ => throw other).unsafeRun(), Left(other))
  }

  // --- guarantee ---------------------------------------------------------------

  test("guarantee runs nothing until the program runs") {
    val calls = counter()
    val io = IO.pure(1).guarantee(tick(calls))
    assertEquals(calls.get, 0)
    assertEquals(io.unsafeRun(), Right(1))
  }

  test("guarantee runs the finalizer once after a success, and keeps the value") {
    val calls = counter()
    assertEquals(IO.pure(1).guarantee(tick(calls)).unsafeRun(), Right(1))
    assertEquals(calls.get, 1)
  }

  test("guarantee runs the finalizer once after an error, and keeps the error") {
    val calls = counter()
    assertEquals(IO.raiseError[Int](boom).guarantee(tick(calls)).unsafeRun(), Left(boom))
    assertEquals(calls.get, 1)
  }

  test("guarantee runs the finalizer after the program, not before") {
    val calls = counter()
    val io = IO.delay(calls.get).guarantee(tick(calls))
    assertEquals(io.unsafeRun(), Right(0))
  }

  test("a finalizer that fails after a success is the program's error, and runs once") {
    val calls = counter()
    val failing = tick(calls).flatMap(_ => IO.raiseError[Unit](other))
    assertEquals(IO.pure(1).guarantee(failing).unsafeRun(), Left(other))
    assertEquals(calls.get, 1)
  }

  test("when the program and the finalizer both fail, the program's error wins") {
    val calls = counter()
    val failing = tick(calls).flatMap(_ => IO.raiseError[Unit](other))
    assertEquals(IO.raiseError[Int](boom).guarantee(failing).unsafeRun(), Left(boom))
    assertEquals(calls.get, 1)
  }

  test("a million nested guarantees each run their finalizer") {
    val calls = counter()
    val io = (1 to deep).foldLeft(IO.pure(0))((acc, _) => acc.guarantee(tick(calls)))
    assertEquals(io.unsafeRun(), Right(0))
    assertEquals(calls.get, deep)
  }
end Exercise02Spec
