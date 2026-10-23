package advfp.ch06fibersandschedulers

import advfp.core.CourseSuite

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import Exercise03.*

class Exercise03Spec extends CourseSuite:

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

  // Two programs that each wait for the other. Run one after the other, the
  // first one waits in vain and reports `false`.
  private def meeting(): (IO[Boolean], IO[Boolean]) =
    val a = CountDownLatch(1)
    val b = CountDownLatch(1)
    val first = IO.delay { a.countDown(); b.await(2, TimeUnit.SECONDS) }
    val second = IO.delay { b.countDown(); a.await(2, TimeUnit.SECONDS) }
    (first, second)

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler)

    // --- joinValue -------------------------------------------------------------

    test(s"$name: joinValue of a fiber that succeeded is its value") {
      assertEquals(run(IO.pure(1).start.flatMap(_.joinValue)), Outcome.Succeeded(1))
    }

    test(s"$name: joinValue of a fiber that failed fails with its error") {
      assertEquals(run(IO.raiseError[Int](boom).start.flatMap(_.joinValue)), Outcome.Errored(boom))
    }

    test(s"$name: joinValue of a canceled fiber fails with FiberCanceled") {
      val io = IO.never.start.flatMap(fiber => fiber.cancel.flatMap(_ => fiber.joinValue))
      run(io) match
        case Outcome.Errored(_: FiberCanceled) => ()
        case other => fail(s"expected Errored(FiberCanceled), got $other")
    }

    test(s"$name: joinValue's error is the fiber's, and can be handled") {
      val io = IO
        .raiseError[String](boom)
        .start
        .flatMap(_.joinValue)
        .handleErrorWith(e => IO.pure(e.getMessage))
      assertEquals(run(io), Outcome.Succeeded("boom"))
    }

    // --- both ------------------------------------------------------------------

    test(s"$name: both pairs the two values") {
      assertEquals(run(both(IO.pure(1), IO.pure("a"))), Outcome.Succeeded((1, "a")))
    }

    test(s"$name: both runs the two programs at the same time") {
      val (first, second) = meeting()
      assertEquals(run(both(first, second)), Outcome.Succeeded((true, true)))
    }

    test(s"$name: both runs nothing until the program runs") {
      val runs = AtomicInteger(0)
      val io = both(IO.delay(runs.incrementAndGet()), IO.delay(runs.incrementAndGet()))
      assertEquals(runs.get, 0)
      run(io) match
        // The two run at once, so either may count first.
        case Outcome.Succeeded((x, y)) => assertEquals(Set(x, y), Set(1, 2))
        case other => fail(s"expected both to succeed, got $other")
    }

    test(s"$name: both fails with the second's error") {
      assertEquals(run(both(IO.pure(1), IO.raiseError[Int](boom))), Outcome.Errored(boom))
    }

    test(s"$name: both fails with the first's error, and the second is stopped") {
      val ticks = AtomicInteger(0)
      val io = both(IO.sleep(20.millis).flatMap(_ => IO.raiseError[Int](boom)), spin(ticks))
      assertEquals(run(io), Outcome.Errored(boom))
      val stoppedAt = ticks.get
      Thread.sleep(100)
      assertEquals(ticks.get, stoppedAt, "the second program kept running after both returned")
    }
  end for
end Exercise03Spec
