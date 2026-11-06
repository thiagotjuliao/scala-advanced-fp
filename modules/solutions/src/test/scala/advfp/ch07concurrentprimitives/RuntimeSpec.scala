package advfp.ch07concurrentprimitives

import advfp.core.CourseSuite

import java.util.concurrent.{
  ConcurrentLinkedQueue,
  CountDownLatch,
  CyclicBarrier,
  TimeUnit,
  TimeoutException
}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** The invariants of the runtime in `IO.scala`, which the chapter gives rather
  * than asks for: chapter 06's races, checked again on the new loop, and the
  * claims of the document's "Inside the regions" section. Not an exercise, so it
  * sits in the answer key only.
  *
  * A race is forced, not hoped for: two fibers meet at a `CyclicBarrier` and
  * then do the two racing things at once, a few thousand times, so that both
  * orders happen. Whatever goes wrong inside the runtime — a fiber completed
  * twice trips an `assert` on whichever thread did it — escapes the task, and
  * the recording scheduler below collects it.
  */
class RuntimeSpec extends CourseSuite:

  // A scheduler that remembers every exception that escaped a task. A pool
  // thread or a virtual thread would otherwise print it and carry on.
  private final class Recording(underlying: Scheduler) extends Scheduler:
    val escaped = ConcurrentLinkedQueue[Throwable]()

    def execute(task: Runnable): Unit =
      underlying.execute: () =>
        try task.run()
        catch
          case t: Throwable =>
            escaped.add(t)
            ()

    def shutdown(): Unit = underlying.shutdown()

  private val schedulers =
    List("pool(4)" -> Recording(Scheduler.pool(4)), "virtual" -> Recording(Scheduler.virtual()))

  private val onePool = Scheduler.pool(1)

  override def afterAll(): Unit =
    onePool.shutdown()
    schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private val rounds = 2_000

  // Time for a fiber to reach its wait. A fiber late to it fails no claim below:
  // it reaches the same end by another of the orders being tested.
  private val settle = IO.sleep(30.millis)

  // What a program wrote, for the order finalizers run in.
  private final class Log:
    private val lines = AtomicReference(Vector.empty[String])

    def write(line: String): IO[Unit] = IO.delay(lines.updateAndGet(_ :+ line)).map(_ => ())

    def read: Vector[String] = lines.get

  // A cancel aimed at a region that ends on its own can lose that race under a
  // loaded machine, and then the fiber ends normally. Measured 2026-11-06: a GC
  // pause during `sbt verify` swallowed a 70 ms window. The late run still has to
  // be coherent, it only tests less.
  private def assertCanceledUnlessLate(
      outcome: Outcome[Outcome[Unit]],
      log: Log,
      canceled: Vector[String],
      late: Vector[String]
  )(using munit.Location): Unit =
    outcome match
      case Outcome.Succeeded(Outcome.Succeeded(_)) => assertEquals(log.read, late)
      case _ =>
        assertEquals(outcome, Outcome.Succeeded(Outcome.Canceled))
        assertEquals(log.read, canceled)

  // A barrier with a deadline: if the other side never comes, the fiber fails
  // instead of holding its thread for good.
  private def meet(barrier: CyclicBarrier): Unit =
    val _ = barrier.await(5, TimeUnit.SECONDS)

  private final class Boom extends RuntimeException("boom")

  private val boom = Boom()

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler)

    def assertNothingEscaped(): Unit =
      val escaped = scheduler.escaped.asScala.toList
      scheduler.escaped.clear()
      assertEquals(escaped.map(_.toString), Nil, "an exception escaped a task")

    // --- a suspension is resumed once ------------------------------------------

    test(s"$name: a callback called twice resumes the fiber once, with the first value") {
      val after = AtomicInteger(0)
      val io = IO
        .async[Int] { callback =>
          callback(Right(1))
          callback(Right(2))
        }
        .flatMap(n => IO.delay(after.incrementAndGet()).map(_ => n))
      assertEquals(run(io), Outcome.Succeeded(1))
      assertEquals(after.get, 1)
      assertNothingEscaped()
    }

    test(s"$name: register that throws after calling back keeps the value") {
      val io = IO.async[Int] { callback =>
        callback(Right(1))
        throw boom
      }
      assertEquals(run(io), Outcome.Succeeded(1))
    }

    test(s"$name: register that throws before calling back fails the fiber") {
      assertEquals(run(IO.async[Int](_ => throw boom)), Outcome.Errored(boom))
    }

    // --- a cancel of a suspended fiber -----------------------------------------

    test(s"$name: a suspended fiber is canceled at once, and a late callback is ignored") {
      val registered = CountDownLatch(1)
      val saved = AtomicReference[Either[Throwable, Int] => Unit](_ => ())
      val waiting = IO.async[Int] { callback =>
        saved.set(callback)
        registered.countDown()
      }
      val io =
        for
          fiber <- waiting.start
          _ <- IO.delay(registered.await(5, TimeUnit.SECONDS))
          _ <- fiber.cancel
          first <- fiber.join
          _ <- IO.delay(saved.get()(Right(1))) // too late: the suspension is claimed
          second <- fiber.join
        yield (first, second)
      assertEquals(run(io), Outcome.Succeeded((Outcome.Canceled, Outcome.Canceled)))
      assertNothingEscaped()
    }

    // --- the races ---------------------------------------------------------------

    test(s"$name: a cancel that races the fiber into a suspension is never lost") {
      // The fiber is on its way into `never` while the cancel arrives: if
      // neither saw the other, the fiber would wait forever and so would
      // `cancel`.
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            fiber <- IO.delay(meet(barrier)).flatMap(_ => IO.never).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => fiber.cancel).start
            ended <- canceler.join
            outcome <- fiber.join
          yield (ended, outcome)
        assertEquals(run(io), Outcome.Succeeded((Outcome.Succeeded(()), Outcome.Canceled)))
      assertNothingEscaped()
    }

    test(s"$name: a callback and a cancel at the same moment end the fiber exactly once") {
      (1 to rounds).foreach: _ =>
        val registered = CountDownLatch(1)
        val saved = AtomicReference[Either[Throwable, Int] => Unit](_ => ())
        val barrier = CyclicBarrier(2)
        val waiting = IO.async[Int] { callback =>
          saved.set(callback)
          registered.countDown()
        }
        val io =
          for
            fiber <- waiting.flatMap(IO.pure).start
            _ <- IO.delay(registered.await(5, TimeUnit.SECONDS))
            resumer <- IO.delay { meet(barrier); saved.get()(Right(1)) }.start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => fiber.cancel).start
            _ <- resumer.join
            ended <- canceler.join
            outcome <- fiber.join
          yield (ended, outcome)
        run(io) match
          case Outcome.Succeeded((Outcome.Succeeded(()), outcome)) =>
            assert(outcome == Outcome.Canceled || outcome == Outcome.Succeeded(1), outcome)
          case other => fail(s"the cancel did not end cleanly: $other")
      assertNothingEscaped()
    }

    test(s"$name: a fiber that ends while others join it tells every one of them") {
      // The fiber and two joiners leave a barrier together: the joiners
      // register while the fiber is ending, and while each other registers.
      // A join that lands between a read of the waiters and the write that
      // replaces them is never told, and waits for good.
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(3)
        val io =
          for
            fiber <- IO.delay(meet(barrier)).map(_ => 7).start
            first <- IO.delay(meet(barrier)).flatMap(_ => fiber.join).start
            second <- IO.delay(meet(barrier)).flatMap(_ => fiber.join).start
            a <- first.join
            b <- second.join
          yield (a, b)
        val told = Outcome.Succeeded(Outcome.Succeeded(7))
        assertEquals(run(io), Outcome.Succeeded((told, told)))
      assertNothingEscaped()
    }

    test(s"$name: racePair decides once when both fibers end at the same moment") {
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io = IO
          .racePair(IO.delay(meet(barrier)).map(_ => "a"), IO.delay(meet(barrier)).map(_ => "b"))
          .flatMap:
            case Left((outcome, other)) => other.join.map(_ => ("a", outcome))
            case Right((other, outcome)) => other.join.map(_ => ("b", outcome))
        run(io) match
          case Outcome.Succeeded((side, Outcome.Succeeded(value))) =>
            assertEquals(value, side)
          case other => fail(s"racePair did not decide cleanly: $other")
      assertNothingEscaped()
    }

    // --- finalizers --------------------------------------------------------------

    test(s"$name: a finalizer runs when the fiber is canceled while suspended") {
      val log = Log()
      val io =
        for
          fiber <- IO.never.onCancel(log.write("finalized")).start
          _ <- settle
          _ <- fiber.cancel
          outcome <- fiber.join
        yield outcome
      assertEquals(run(io), Outcome.Succeeded(Outcome.Canceled))
      assertEquals(log.read, Vector("finalized"))
    }

    test(s"$name: a finalizer runs when the fiber is canceled at a bind") {
      val log = Log()
      val entered = CountDownLatch(1)
      val leave = CountDownLatch(1)
      val io =
        for
          fiber <- IO
            .delay {
              entered.countDown()
              leave.await(5, TimeUnit.SECONDS)
            }
            .flatMap(_ => log.write("too late"))
            .onCancel(log.write("finalized"))
            .start
          _ <- IO.delay(entered.await(5, TimeUnit.SECONDS))
          canceler <- fiber.cancel.start
          _ <- settle
          _ <- IO.delay(leave.countDown())
          _ <- canceler.join
        yield ()
      assertEquals(run(io), Outcome.Succeeded(()))
      assertEquals(log.read, Vector("finalized"))
    }

    test(s"$name: a finalizer does not run on success or on an error") {
      val log = Log()
      val io =
        for
          a <- IO.pure(1).onCancel(log.write("after success"))
          b <- IO.raiseError[Int](boom).onCancel(log.write("after error")).start.flatMap(_.join)
        yield (a, b)
      assertEquals(run(io), Outcome.Succeeded((1, Outcome.Errored(boom))))
      assertEquals(log.read, Vector.empty)
    }

    test(s"$name: a finalizer is only armed while its program runs") {
      val log = Log()
      val io =
        for
          fiber <- IO.unit.onCancel(log.write("too early")).flatMap(_ => IO.never).start
          _ <- settle
          _ <- fiber.cancel
        yield ()
      assertEquals(run(io), Outcome.Succeeded(()))
      assertEquals(log.read, Vector.empty)
    }

    test(s"$name: finalizers run innermost first, and an error in one stops none of the others") {
      val log = Log()
      val io =
        for
          fiber <- IO.never
            .onCancel(log.write("inner"))
            .onCancel(log.write("middle").flatMap(_ => IO.raiseError(boom)))
            .onCancel(log.write("outer"))
            .start
          _ <- settle
          _ <- fiber.cancel
          outcome <- fiber.join
        yield outcome
      assertEquals(run(io), Outcome.Succeeded(Outcome.Canceled))
      assertEquals(log.read, Vector("inner", "middle", "outer"))
    }

    test(s"$name: cancel waits for the finalizers, and a finalizer that sleeps is not canceled") {
      val log = Log()
      val io =
        for
          fiber <- IO.never
            .onCancel(IO.sleep(30.millis).flatMap(_ => log.write("slept")))
            .start
          _ <- settle
          _ <- fiber.cancel
          seen <- IO.delay(log.read)
        yield seen
      assertEquals(run(io), Outcome.Succeeded(Vector("slept")))
    }

    test(s"$name: a million nested finalizers all run, on one stack") {
      val ran = AtomicInteger(0)
      val nested = (1 to 1_000_000).foldLeft(IO.never: IO[Unit]): (io, _) =>
        io.onCancel(IO.delay(ran.incrementAndGet()).map(_ => ()))
      val io =
        for
          fiber <- nested.start
          _ <- settle
          _ <- fiber.cancel
        yield ()
      assertEquals(io.unsafeRun(scheduler, 30.seconds), Outcome.Succeeded(()))
      assertEquals(ran.get, 1_000_000)
    }

    test(s"$name: a finalizer runs exactly once when a callback races the cancel") {
      (1 to rounds).foreach: _ =>
        val ran = AtomicInteger(0)
        val registered = CountDownLatch(1)
        val saved = AtomicReference[Either[Throwable, Int] => Unit](_ => ())
        val barrier = CyclicBarrier(2)
        val waiting = IO.async[Int] { callback =>
          saved.set(callback)
          registered.countDown()
        }
        val io =
          for
            fiber <- waiting.onCancel(IO.delay(ran.incrementAndGet()).map(_ => ())).start
            _ <- IO.delay(registered.await(5, TimeUnit.SECONDS))
            resumer <- IO.delay { meet(barrier); saved.get()(Right(1)) }.start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => fiber.cancel).start
            _ <- resumer.join
            _ <- canceler.join
            outcome <- fiber.join
          yield outcome
        run(io) match
          case Outcome.Succeeded(Outcome.Canceled) => assertEquals(ran.get, 1)
          case Outcome.Succeeded(Outcome.Succeeded(1)) => assertEquals(ran.get, 0)
          case other => fail(s"the cancel did not end cleanly: $other")
      assertNothingEscaped()
    }

    // --- regions -----------------------------------------------------------------

    test(s"$name: a region finishes before a cancel takes effect, and nothing after it runs") {
      val log = Log()
      val entered = CountDownLatch(1)
      val io =
        for
          fiber <- IO
            .uncancelable(_ =>
              IO.delay(entered.countDown())
                .flatMap(_ => IO.sleep(30.millis))
                .flatMap(_ => log.write("in the region"))
            )
            .flatMap(_ => log.write("after the region"))
            .start
          _ <- IO.delay(entered.await(5, TimeUnit.SECONDS))
          _ <- fiber.cancel
          outcome <- fiber.join
        yield outcome
      val late = Vector("in the region", "after the region")
      assertCanceledUnlessLate(run(io), log, Vector("in the region"), late)
    }

    test(s"$name: poll lets a cancel in, and the finalizer around it runs") {
      val log = Log()
      val io =
        for
          fiber <- IO
            .uncancelable(poll => poll(IO.never).onCancel(log.write("finalized")))
            .start
          _ <- settle
          _ <- fiber.cancel
          outcome <- fiber.join
        yield outcome
      assertEquals(run(io), Outcome.Succeeded(Outcome.Canceled))
      assertEquals(log.read, Vector("finalized"))
    }

    test(s"$name: a cancel asked for in the region stops the fiber on entering poll") {
      val log = Log()
      val entered = CountDownLatch(1)
      val canceling = CountDownLatch(1)
      val io =
        for
          fiber <- IO
            .uncancelable: poll =>
              IO.delay {
                entered.countDown()
                canceling.await(5, TimeUnit.SECONDS)
              }.flatMap(_ => poll(log.write("in poll")).onCancel(log.write("finalized")))
            .start
          _ <- IO.delay(entered.await(5, TimeUnit.SECONDS))
          canceler <- fiber.cancel.start
          _ <- settle
          _ <- IO.delay(canceling.countDown())
          _ <- canceler.join
          outcome <- fiber.join
        yield outcome
      assertCanceledUnlessLate(run(io), log, Vector("finalized"), Vector("in poll"))
    }

    test(s"$name: poll undoes its own region only, not one around it") {
      val log = Log()
      val io =
        for
          fiber <- IO
            .uncancelable: _ =>
              IO.uncancelable(poll => poll(IO.sleep(100.millis)))
                .flatMap(_ => log.write("still masked"))
            .flatMap(_ => log.write("after the regions"))
            .start
          _ <- settle
          _ <- fiber.cancel
          outcome <- fiber.join
        yield outcome
      val late = Vector("still masked", "after the regions")
      assertCanceledUnlessLate(run(io), log, Vector("still masked"), late)
    }

    test(s"$name: an error leaves the region, and the mask with it") {
      val log = Log()
      val io =
        for
          fiber <- IO
            .uncancelable(_ => IO.raiseError[Unit](boom))
            .handleErrorWith(_ => IO.never)
            .onCancel(log.write("finalized"))
            .start
          _ <- settle
          _ <- fiber.cancel
          outcome <- fiber.join
        yield outcome
      assertEquals(run(io), Outcome.Succeeded(Outcome.Canceled))
      assertEquals(log.read, Vector("finalized"))
    }

    test(s"$name: a region that waits for nothing cannot be canceled, and only times out") {
      val io =
        for
          fiber <- IO.uncancelable(_ => IO.never).start
          _ <- settle
          _ <- fiber.cancel
        yield ()
      val _ = intercept[TimeoutException](io.unsafeRun(scheduler, 200.millis))
    }

    test(s"$name: a fiber started inside a region can still be canceled") {
      val io =
        IO.uncancelable: _ =>
          for
            child <- IO.never.start
            _ <- settle
            _ <- child.cancel
            outcome <- child.join
          yield outcome
      assertEquals(run(io), Outcome.Succeeded(Outcome.Canceled))
    }

    test(s"$name: a cancel at any moment around a region runs the finalizer or the whole region") {
      // Either the fiber never entered the protected part or it finished it, and
      // the finalizer undoes exactly what was done: the count ends where it began.
      (1 to rounds).foreach: _ =>
        val held = AtomicInteger(0)
        val barrier = CyclicBarrier(2)
        val take = IO.delay(held.incrementAndGet()).map(_ => ())
        val give = IO.delay(held.decrementAndGet()).map(_ => ())
        val guarded = IO.uncancelable: poll =>
          take.flatMap(_ => poll(IO.delay(meet(barrier))).onCancel(give)).flatMap(_ => give)
        val io =
          for
            fiber <- guarded.start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => fiber.cancel).start
            _ <- canceler.join
            _ <- fiber.join
          yield ()
        assertEquals(run(io), Outcome.Succeeded(()))
        assertEquals(held.get, 0)
      assertNothingEscaped()
    }
  end for

  // --- on a single thread: the order is forced ------------------------------------

  test("pool(1): a fiber whose callback won still sees a cancel asked for before it resumes") {
    // The callback claims the suspension and queues the loop on the only thread;
    // this fiber keeps that thread until it joins, so the cancel lands in between.
    val log = Log()
    val saved = AtomicReference[Either[Throwable, Int] => Unit](_ => ())
    val io =
      for
        fiber <- IO
          .async[Int](callback => saved.set(callback))
          .onCancel(log.write("finalized"))
          .start
        _ <- settle
        _ <- IO.delay(saved.get()(Right(1)))
        _ <- fiber.cancel
        outcome <- fiber.join
      yield outcome
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(Outcome.Canceled))
    assertEquals(log.read, Vector("finalized"))
  }

  test("pool(1): a waiter in a region is resumed, not canceled, by a callback after the cancel") {
    val log = Log()
    val saved = AtomicReference[Either[Throwable, Int] => Unit](_ => ())
    val io =
      for
        fiber <- IO
          .uncancelable(_ => IO.async[Int](callback => saved.set(callback)))
          .onCancel(log.write("finalized"))
          .start
        _ <- settle
        canceler <- fiber.cancel.start
        _ <- settle
        _ <- IO.delay(saved.get()(Right(1)))
        _ <- canceler.join
        outcome <- fiber.join
      yield outcome
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(Outcome.Succeeded(1)))
    assertEquals(log.read, Vector.empty)
  }
end RuntimeSpec
