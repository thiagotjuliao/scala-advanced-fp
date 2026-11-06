package advfp.ch07concurrentprimitives

import advfp.core.CourseSuite

import java.util.concurrent.{CyclicBarrier, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import Exercise01.*

class Exercise01Spec extends CourseSuite:

  private val schedulers = List("pool(4)" -> Scheduler.pool(4), "virtual" -> Scheduler.virtual())

  override def afterAll(): Unit = schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private final class Boom extends RuntimeException("boom")

  private val boom = Boom()

  // Two attempts read the cell, then meet here, so that neither can write before
  // the other has read: the contention is forced, not hoped for. Only the first
  // two calls meet; a retry goes straight through.
  private final class Contended:
    private val barrier = CyclicBarrier(2)
    val calls = AtomicInteger(0)

    def meet(): Unit =
      if calls.incrementAndGet() <= 2 then
        val _ = barrier.await(5, TimeUnit.SECONDS)

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler, 20.seconds)

    test(s"$name: get reads what of was given, and set replaces it") {
      val io =
        for
          ref <- Ref.of(1)
          first <- ref.get
          _ <- ref.set(2)
          second <- ref.get
        yield (first, second)
      assertEquals(run(io), Outcome.Succeeded((1, 2)))
    }

    test(s"$name: update, getAndUpdate and modify change the cell, and produce what they say") {
      val io =
        for
          ref <- Ref.of(10)
          _ <- ref.update(_ + 1)
          old <- ref.getAndUpdate(_ * 2)
          text <- ref.modify(n => (n + 1, s"was $n"))
          last <- ref.get
        yield (old, text, last)
      assertEquals(run(io), Outcome.Succeeded((11, "was 22", 23)))
    }

    test(s"$name: nothing happens until the program runs") {
      val calls = AtomicInteger(0)
      val io =
        for
          ref <- Ref.of(0)
          update = ref.update { n =>
            calls.incrementAndGet()
            n + 1
          }
          before <- ref.get
          countBefore <- IO.delay(calls.get)
          _ <- update
          _ <- update
          after <- ref.get
        yield (before, countBefore, after)
      assertEquals(run(io), Outcome.Succeeded((0, 0, 2)))
    }

    test(s"$name: running of twice makes two cells") {
      val makeOne = Ref.of(0)
      val io =
        for
          a <- makeOne
          b <- makeOne
          _ <- a.set(1)
          seen <- b.get
        yield seen
      assertEquals(run(io), Outcome.Succeeded(0))
    }

    test(s"$name: two updates that read at once both count, and the loser runs again") {
      val contended = Contended()
      val io =
        for
          ref <- Ref.of(0)
          step = ref.update { n =>
            contended.meet()
            n + 1
          }
          a <- step.start
          b <- step.start
          _ <- a.join
          _ <- b.join
          value <- ref.get
        yield value
      assertEquals(run(io), Outcome.Succeeded(2))
      assertEquals(contended.calls.get, 3, "the attempt that lost must run again, once")
    }

    test(s"$name: two modifies that read at once produce from what each one changed") {
      val contended = Contended()
      val io =
        for
          ref <- Ref.of(0)
          ticket = ref.modify { n =>
            contended.meet()
            (n + 1, n)
          }
          a <- ticket.start
          b <- ticket.start
          ra <- a.join
          rb <- b.join
        yield Set(ra, rb)
      assertEquals(
        run(io),
        Outcome.Succeeded(Set[Outcome[Int]](Outcome.Succeeded(0), Outcome.Succeeded(1)))
      )
    }

    test(s"$name: two getAndUpdates that read at once see each other's write") {
      val contended = Contended()
      val io =
        for
          ref <- Ref.of(0)
          step = ref.getAndUpdate { n =>
            contended.meet()
            n + 1
          }
          a <- step.start
          b <- step.start
          ra <- a.join
          rb <- b.join
        yield Set(ra, rb)
      assertEquals(
        run(io),
        Outcome.Succeeded(Set[Outcome[Int]](Outcome.Succeeded(0), Outcome.Succeeded(1)))
      )
    }

    test(s"$name: a function that throws fails the program, and leaves the cell as it was") {
      val io =
        for
          ref <- Ref.of(1)
          outcome <- ref.update(_ => throw boom).start.flatMap(_.join)
          value <- ref.get
        yield (outcome, value)
      assertEquals(run(io), Outcome.Succeeded((Outcome.Errored(boom), 1)))
    }

    test(s"$name: eight fibers and forty thousand updates lose none") {
      def times(n: Int, io: IO[Unit]): IO[Unit] =
        if n == 0 then IO.unit else io.flatMap(_ => times(n - 1, io))
      val io =
        for
          ref <- Ref.of(0)
          fibers <- (1 to 8).foldLeft(IO.pure(List.empty[Fiber[Unit]])): (acc, _) =>
            acc.flatMap(fs => times(5000, ref.update(_ + 1)).start.map(_ :: fs))
          _ <- fibers.foldLeft(IO.unit)((acc, f) => acc.flatMap(_ => f.join.map(_ => ())))
          value <- ref.get
        yield value
      assertEquals(run(io), Outcome.Succeeded(40000))
    }
  end for
end Exercise01Spec
