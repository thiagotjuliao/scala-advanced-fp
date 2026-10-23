package advfp.ch06fibersandschedulers

import advfp.core.CourseSuite

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, CyclicBarrier, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import scala.jdk.CollectionConverters.*

/** The invariants of the runtime in `IO.scala`, which the chapter gives rather
  * than asks for: the claims of the document's "Inside the runtime" section,
  * checked. Not an exercise, so it sits in the answer key only.
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

  override def afterAll(): Unit = schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private val rounds = 2_000

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
  end for
end RuntimeSpec
