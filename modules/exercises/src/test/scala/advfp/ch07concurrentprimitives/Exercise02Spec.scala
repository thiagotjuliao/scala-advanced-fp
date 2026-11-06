package advfp.ch07concurrentprimitives

import advfp.core.CourseSuite

import java.util.concurrent.{CyclicBarrier, TimeUnit}

import scala.concurrent.duration.*

import Exercise02.*

class Exercise02Spec extends CourseSuite:

  private val onePool = Scheduler.pool(1)

  private val schedulers = List("pool(4)" -> Scheduler.pool(4), "virtual" -> Scheduler.virtual())

  override def afterAll(): Unit =
    onePool.shutdown()
    schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private def meet(barrier: CyclicBarrier): Unit =
    val _ = barrier.await(5, TimeUnit.SECONDS)

  // Starts `n` copies of `io` and collects what each produced, in order.
  private def many[A](n: Int)(io: IO[A]): IO[List[Outcome[A]]] =
    val started = (1 to n).foldLeft(IO.pure(List.empty[Fiber[A]])): (acc, _) =>
      acc.flatMap(fs => io.start.map(_ :: fs))
    started.flatMap: fibers =>
      fibers.foldLeft(IO.pure(List.empty[Outcome[A]])): (acc, f) =>
        acc.flatMap(os => f.join.map(_ :: os))

  private val rounds = 2_000

  // --- on a single thread: waiting must not hold it -----------------------------

  test("pool(1): a fiber waiting in get does not stop the one that completes") {
    val io =
      for
        d <- Deferred[Int]
        waiter <- d.get.start
        _ <- IO.sleep(20.millis)
        _ <- d.complete(1)
        o <- waiter.join
      yield o
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(Outcome.Succeeded(1)))
  }

  test("pool(1): a thousand fibers wait at once, and all are told") {
    val io =
      for
        d <- Deferred[Int]
        waiters <- many(1000)(d.get).start
        _ <- IO.sleep(50.millis)
        _ <- d.complete(7)
        o <- waiters.join
      yield o
    assertEquals(
      io.unsafeRun(onePool, 5.seconds),
      Outcome.Succeeded(Outcome.Succeeded(List.fill(1000)(Outcome.Succeeded(7))))
    )
  }

  test("pool(1): a fiber waiting in get is canceled, and a later complete still works") {
    val io =
      for
        d <- Deferred[Int]
        waiter <- d.get.start
        _ <- IO.sleep(20.millis)
        _ <- waiter.cancel
        o <- waiter.join
        filled <- d.complete(1)
      yield (o, filled)
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded((Outcome.Canceled, true)))
  }

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler, 20.seconds)

    test(s"$name: get after complete produces the value, every time") {
      val io =
        for
          d <- Deferred[Int]
          _ <- d.complete(1)
          a <- d.get
          b <- d.get
        yield (a, b)
      assertEquals(run(io), Outcome.Succeeded((1, 1)))
    }

    test(s"$name: only the first complete counts, and each says whether it did") {
      val io =
        for
          d <- Deferred[Int]
          first <- d.complete(1)
          second <- d.complete(2)
          value <- d.get
        yield (first, second, value)
      assertEquals(run(io), Outcome.Succeeded((true, false, 1)))
    }

    test(s"$name: tryGet does not wait") {
      val io =
        for
          d <- Deferred[Int]
          before <- d.tryGet
          _ <- d.complete(1)
          after <- d.tryGet
        yield (before, after)
      assertEquals(run(io), Outcome.Succeeded((None, Some(1))))
    }

    test(s"$name: nothing happens until the program runs") {
      val io =
        for
          d <- Deferred[Int]
          unused = d.complete(1)
          seen <- d.tryGet
          _ <- unused
          after <- d.tryGet
        yield (seen, after)
      assertEquals(run(io), Outcome.Succeeded((None, Some(1))))
    }

    test(s"$name: two completes at the same moment: one wins, and everyone sees its value") {
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            d <- Deferred[Int]
            a <- IO.delay(meet(barrier)).flatMap(_ => d.complete(1)).start
            b <- IO.delay(meet(barrier)).flatMap(_ => d.complete(2)).start
            ra <- a.join
            rb <- b.join
            value <- d.get
          yield (ra, rb, value)
        run(io) match
          case Outcome.Succeeded((Outcome.Succeeded(true), Outcome.Succeeded(false), 1)) => ()
          case Outcome.Succeeded((Outcome.Succeeded(false), Outcome.Succeeded(true), 2)) => ()
          case other => fail(s"not exactly one winner: $other")
    }

    test(s"$name: a get that races a complete is told, on whichever side it lands") {
      // Two readers register while the value arrives and while each other
      // registers: one that lands between a read of the waiters and the write
      // that replaces them is never told, and waits for good.
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(3)
        val io =
          for
            d <- Deferred[Int]
            a <- IO.delay(meet(barrier)).flatMap(_ => d.get).start
            b <- IO.delay(meet(barrier)).flatMap(_ => d.get).start
            c <- IO.delay(meet(barrier)).flatMap(_ => d.complete(9)).start
            ra <- a.join
            rb <- b.join
            _ <- c.join
          yield (ra, rb)
        assertEquals(
          io.unsafeRun(scheduler, 5.seconds),
          Outcome.Succeeded((Outcome.Succeeded(9), Outcome.Succeeded(9)))
        )
    }
  end for
end Exercise02Spec
