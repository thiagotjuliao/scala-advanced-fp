package advfp.ch06fibersandschedulers

import advfp.core.CourseSuite

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import scala.concurrent.duration.*

import Exercise04.*

class Exercise04Spec extends CourseSuite:

  // The same claims, against both schedulers: a program that only suspends
  // must not care which one runs it.
  private val schedulers = List("pool(4)" -> Scheduler.pool(4), "virtual" -> Scheduler.virtual())

  override def afterAll(): Unit = schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private final class Boom extends RuntimeException("boom")

  private val boom = Boom()

  // A program that never ends on its own, and counts how far it got: a
  // cancellation point at every step. It also stops when its scheduler is shut
  // down, so that an answer which forgets to cancel it does not leave it
  // spinning in sbt until sbt exits.
  private def spin(ticks: AtomicInteger): IO[Unit] =
    IO.delay {
      if Thread.currentThread.isInterrupted then throw InterruptedException()
      ticks.incrementAndGet()
    }.flatMap(_ => spin(ticks))

  // Asserts that `ticks` stays where it is: whatever was counting has stopped.
  private def assertStopped(ticks: AtomicInteger): Unit =
    val stoppedAt = ticks.get
    Thread.sleep(100)
    assertEquals(ticks.get, stoppedAt, "the loser kept running after the race had ended")

  // What a program wrote, for the effects a loser must never get to.
  private final class Log:
    private val lines = AtomicReference(Vector.empty[String])

    def write(line: String): IO[Unit] = IO.delay(lines.updateAndGet(_ :+ line)).map(_ => ())

    def read: Vector[String] = lines.get

  // A program that waits until the test lets it go, and then writes "too
  // late". The test lets it go only after the race has ended, so no timing is
  // involved: a loser that was canceled has ended, its suspension claimed or
  // never reached, and the release does nothing; a loser still alive is
  // resumed by it — whether it suspended before the release or after — and
  // writes. A version with two sleeps of different lengths failed a correct
  // answer when a GC pause made both timers fire at once.
  private final class Held(log: Log):
    private val released = AtomicBoolean(false)
    private val waiting = AtomicReference[Option[Either[Throwable, Unit] => Unit]](None)

    val program: IO[Unit] =
      IO.async[Unit] { callback =>
        waiting.set(Some(callback))
        if released.get then callback(Right(()))
      }.flatMap(_ => log.write("too late"))

    // The release first, then the callback: `program` does the same two in the
    // other order, so one of them always calls back. `async` ignores a second
    // call.
    def letGo(): Unit =
      released.set(true)
      waiting.get.foreach(_(Right(())))

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler)

    // --- race ------------------------------------------------------------------

    test(s"$name: race produces the value of the program that ends first") {
      assertEquals(run(race(IO.pure(1), IO.never)), Outcome.Succeeded(Left(1)))
      assertEquals(run(race(IO.never, IO.pure("b"))), Outcome.Succeeded(Right("b")))
    }

    test(s"$name: race is won by the faster program, whichever side it is on") {
      val fast = IO.sleep(10.millis).map(_ => "fast")
      val slow = IO.sleep(2.seconds).map(_ => "slow")
      assertEquals(run(race(fast, slow)), Outcome.Succeeded(Left("fast")))
      assertEquals(run(race(slow, fast)), Outcome.Succeeded(Right("fast")))
    }

    test(s"$name: race cancels the loser before it ends") {
      val ticks = AtomicInteger(0)
      val io = race(IO.sleep(20.millis).map(_ => 1), spin(ticks))
      assertEquals(run(io), Outcome.Succeeded(Left(1)))
      assertStopped(ticks)
    }

    test(s"$name: the loser's later effects never happen") {
      val log = Log()
      val loser = Held(log)
      assertEquals(run(race(IO.pure(1), loser.program)), Outcome.Succeeded(Left(1)))
      loser.letGo()
      Thread.sleep(100) // time for a loser that was not stopped to write
      assertEquals(log.read, Vector.empty)
    }

    test(s"$name: a winner that fails fails the race, and the loser is canceled") {
      val ticks = AtomicInteger(0)
      val io = race(IO.sleep(20.millis).flatMap(_ => IO.raiseError[Int](boom)), spin(ticks))
      assertEquals(run(io), Outcome.Errored(boom))
      assertStopped(ticks)
    }

    test(s"$name: race runs nothing until the program runs") {
      val log = Log()
      val io = race(log.write("a"), log.write("b"))
      assertEquals(log.read, Vector.empty)
      run(io) match
        case Outcome.Succeeded(_) => assert(log.read.nonEmpty, "the winner's effect never ran")
        case other => fail(s"expected the race to succeed, got $other")
    }

    // --- timeout ---------------------------------------------------------------

    test(s"$name: a program that ends in time keeps its value") {
      assertEquals(run(IO.sleep(10.millis).map(_ => 1).timeout(2.seconds)), Outcome.Succeeded(1))
    }

    test(s"$name: a program that ends in time keeps its error") {
      assertEquals(run(IO.raiseError[Int](boom).timeout(2.seconds)), Outcome.Errored(boom))
    }

    test(s"$name: a program that runs too long fails with TimeoutException") {
      run(IO.never.timeout(50.millis)) match
        case Outcome.Errored(_: TimeoutException) => ()
        case other => fail(s"expected Errored(TimeoutException), got $other")
    }

    test(s"$name: a program that runs too long is stopped") {
      val log = Log()
      val slow = Held(log)
      run(slow.program.timeout(20.millis)) match
        case Outcome.Errored(_: TimeoutException) => ()
        case other => fail(s"expected Errored(TimeoutException), got $other")
      slow.letGo()
      Thread.sleep(100) // time for a program that was not stopped to write
      assertEquals(log.read, Vector.empty)
    }
  end for
end Exercise04Spec
