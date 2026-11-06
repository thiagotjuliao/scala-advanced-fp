package advfp.ch07concurrentprimitives

import advfp.core.CourseSuite

import java.util.concurrent.{CyclicBarrier, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import scala.concurrent.duration.*

import Exercise03.*

class Exercise03Spec extends CourseSuite:

  private val onePool = Scheduler.pool(1)

  private val schedulers = List("pool(4)" -> Scheduler.pool(4), "virtual" -> Scheduler.virtual())

  override def afterAll(): Unit =
    onePool.shutdown()
    schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private final class Boom extends RuntimeException("boom")

  private val boom = Boom()

  private def meet(barrier: CyclicBarrier): Unit =
    val _ = barrier.await(5, TimeUnit.SECONDS)

  // Goes to the back of the scheduler's line: on `pool(1)` the other fibers get the
  // thread. No timer, since a sleep per look takes 15 ms on Windows, and a test of
  // a few thousand rounds outlives munit's 30 seconds.
  private val cede: IO[Unit] = IO.async(callback => callback(Right(())))

  // Goes on once `n` fibers are in the queue.
  private def untilWaiting(sem: Semaphore, n: Int): IO[Unit] =
    sem.waiting.flatMap: seen =>
      if seen == n then IO.unit else cede.flatMap(_ => untilWaiting(sem, n))

  private final class Log:
    private val lines = AtomicReference(Vector.empty[Int])

    def write(line: Int): IO[Unit] = IO.delay(lines.updateAndGet(_ :+ line)).map(_ => ())

    def read: Vector[Int] = lines.get

  private def runAll[A](programs: List[IO[A]]): IO[List[Outcome[A]]] =
    val started = programs.foldLeft(IO.pure(List.empty[Fiber[A]])): (acc, p) =>
      acc.flatMap(fs => p.start.map(_ :: fs))
    started.flatMap: fibers =>
      fibers.foldLeft(IO.pure(List.empty[Outcome[A]])): (acc, f) =>
        acc.flatMap(os => f.join.map(_ :: os))

  private val rounds = 2_000

  test("a negative number of permits is rejected where it is written") {
    val _ = intercept[IllegalArgumentException](Semaphore(-1))
  }

  // --- on a single thread: every order below is forced --------------------------

  test("pool(1): a fiber waiting for a permit does not stop the one that releases") {
    val io =
      for
        sem <- Semaphore(0)
        waiter <- sem.acquire.start
        _ <- untilWaiting(sem, 1)
        _ <- sem.release
        o <- waiter.join
      yield o
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(Outcome.Succeeded(())))
  }

  test("pool(1): a release with someone waiting hands the permit over: none is left to steal") {
    val io =
      for
        sem <- Semaphore(0)
        waiter <- sem.acquire.start
        _ <- untilWaiting(sem, 1)
        _ <- sem.release
        left <- sem.available
        _ <- waiter.join
      yield left
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(0))
  }

  test("pool(1): a fiber arriving behind a waiter does not overtake it") {
    val log = Log()
    val io =
      for
        sem <- Semaphore(0)
        first <- sem.acquire.flatMap(_ => log.write(1)).start
        _ <- untilWaiting(sem, 1)
        _ <- sem.release
        second <- sem.acquire.flatMap(_ => log.write(2)).start
        _ <- first.join
        _ <- sem.release
        _ <- second.join
      yield ()
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(()))
    assertEquals(log.read, Vector(1, 2))
  }

  test("pool(1): a fiber canceled just after being handed the permit gives it back") {
    // The release hands the permit over and queues the waiter on the only
    // thread, and this fiber keeps that thread until it joins, so the cancel
    // always lands before the waiter has run again.
    val io =
      for
        sem <- Semaphore(0)
        waiter <- sem.acquire.start
        _ <- untilWaiting(sem, 1)
        _ <- sem.release
        _ <- waiter.cancel
        o <- waiter.join
        left <- sem.available
      yield (o, left)
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded((Outcome.Canceled, 1)))
  }

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler, 20.seconds)

    test(s"$name: permits are counted, and a release puts one back") {
      val io =
        for
          sem <- Semaphore(2)
          start <- sem.available
          _ <- sem.acquire
          one <- sem.available
          _ <- sem.acquire
          none <- sem.available
          _ <- sem.release
          back <- sem.available
        yield (start, one, none, back)
      assertEquals(run(io), Outcome.Succeeded((2, 1, 0, 1)))
    }

    test(s"$name: nothing happens until the program runs") {
      val io =
        for
          sem <- Semaphore(1)
          unused = sem.acquire
          before <- sem.available
          _ <- unused
          after <- sem.available
        yield (before, after)
      assertEquals(run(io), Outcome.Succeeded((1, 0)))
    }

    test(s"$name: with no permit left a fiber waits, until a release lets it through") {
      val io =
        for
          sem <- Semaphore(1)
          _ <- sem.acquire
          waiter <- sem.acquire.start
          _ <- untilWaiting(sem, 1)
          _ <- IO.sleep(20.millis)
          stillThere <- sem.waiting
          _ <- sem.release
          o <- waiter.join
          after <- sem.waiting
        yield (stillThere, o, after)
      assertEquals(run(io), Outcome.Succeeded((1, Outcome.Succeeded(()), 0)))
    }

    test(s"$name: fibers go through in the order they arrived") {
      val log = Log()
      val io =
        for
          sem <- Semaphore(0)
          fibers <- (1 to 8).foldLeft(IO.pure(List.empty[Fiber[Unit]])): (acc, i) =>
            for
              fs <- acc
              f <- sem.acquire.flatMap(_ => log.write(i)).flatMap(_ => sem.release).start
              _ <- untilWaiting(sem, i)
            yield f :: fs
          _ <- sem.release
          _ <- fibers.foldLeft(IO.unit)((acc, f) => acc.flatMap(_ => f.join.map(_ => ())))
        yield ()
      assertEquals(run(io), Outcome.Succeeded(()))
      assertEquals(log.read, (1 to 8).toVector)
    }

    test(s"$name: with three permits, exactly three run at once") {
      val running = AtomicInteger(0)
      val most = AtomicInteger(0)
      val job = IO
        .delay {
          val now = running.incrementAndGet()
          val _ = most.accumulateAndGet(now, math.max)
        }
        .flatMap(_ => IO.sleep(2.millis))
        .flatMap(_ => IO.delay(running.decrementAndGet()))
        .map(_ => ())
      val io = Semaphore(3).flatMap(sem => runAll(List.fill(60)(sem.withPermit(job))))
      run(io) match
        case Outcome.Succeeded(outcomes) =>
          assertEquals(outcomes.toSet, Set[Outcome[Unit]](Outcome.Succeeded(())))
        case other => fail(s"expected every job to succeed: $other")
      assertEquals(most.get, 3)
    }

    test(s"$name: a canceled waiter leaves the queue, and the next one takes its place") {
      val log = Log()
      val io =
        for
          sem <- Semaphore(0)
          first <- sem.acquire.flatMap(_ => log.write(1)).start
          _ <- untilWaiting(sem, 1)
          second <- sem.acquire.flatMap(_ => log.write(2)).start
          _ <- untilWaiting(sem, 2)
          third <- sem.acquire.flatMap(_ => log.write(3)).start
          _ <- untilWaiting(sem, 3)
          _ <- second.cancel
          queued <- sem.waiting
          _ <- sem.release
          _ <- first.join
          _ <- sem.release
          _ <- third.join
          left <- sem.available
        yield (queued, left)
      assertEquals(run(io), Outcome.Succeeded((2, 0)))
      assertEquals(log.read, Vector(1, 3))
    }

    test(s"$name: canceling the only waiter loses nothing: the next release is a permit") {
      val io =
        for
          sem <- Semaphore(0)
          waiter <- sem.acquire.start
          _ <- untilWaiting(sem, 1)
          _ <- waiter.cancel
          _ <- sem.release
          left <- sem.available
        yield left
      assertEquals(run(io), Outcome.Succeeded(1))
    }

    test(s"$name: withPermit gives back the permit when the program succeeds") {
      val io =
        for
          sem <- Semaphore(1)
          value <- sem.withPermit(sem.available)
          after <- sem.available
        yield (value, after)
      assertEquals(run(io), Outcome.Succeeded((0, 1)))
    }

    test(s"$name: withPermit gives back the permit when the program fails") {
      val io =
        for
          sem <- Semaphore(1)
          o <- sem.withPermit(IO.raiseError[Int](boom)).start.flatMap(_.join)
          after <- sem.available
        yield (o, after)
      assertEquals(run(io), Outcome.Succeeded((Outcome.Errored(boom), 1)))
    }

    test(s"$name: withPermit gives back the permit when the program is canceled") {
      val io =
        for
          sem <- Semaphore(1)
          f <- sem.withPermit(IO.never).start
          _ <- untilAvailable(sem, 0)
          _ <- f.cancel
          o <- f.join
          after <- sem.available
        yield (o, after)
      assertEquals(run(io), Outcome.Succeeded((Outcome.Canceled, 1)))
    }

    test(s"$name: withPermit canceled while waiting never runs the program") {
      val ran = AtomicInteger(0)
      val io =
        for
          sem <- Semaphore(0)
          f <- sem.withPermit(IO.delay(ran.incrementAndGet())).start
          _ <- untilWaiting(sem, 1)
          _ <- f.cancel
          _ <- sem.release
          left <- sem.available
        yield left
      assertEquals(run(io), Outcome.Succeeded(1))
      assertEquals(ran.get, 0)
    }

    test(s"$name: a cancel at any moment of withPermit leaves the permits as they were") {
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            sem <- Semaphore(1)
            f <- IO.delay(meet(barrier)).flatMap(_ => sem.withPermit(IO.unit)).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => f.cancel).start
            _ <- canceler.join
            _ <- f.join
            left <- sem.available
          yield left
        assertEquals(run(io), Outcome.Succeeded(1))
    }

    test(s"$name: a cancel that races a hand-off inside withPermit loses no permit") {
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            sem <- Semaphore(0)
            f <- sem.withPermit(IO.unit).start
            _ <- untilWaiting(sem, 1)
            releaser <- IO.delay(meet(barrier)).flatMap(_ => sem.release).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => f.cancel).start
            _ <- releaser.join
            _ <- canceler.join
            _ <- f.join
            left <- sem.available
          yield left
        assertEquals(run(io), Outcome.Succeeded(1))
    }

    test(s"$name: a release canceled halfway never takes a waiter off the queue and leaves it") {
      // Either the release did nothing, and the waiter is still queued, or it
      // took the waiter off the queue, and then it must have woken it.
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            sem <- Semaphore(0)
            waiter <- sem.acquire.start
            _ <- untilWaiting(sem, 1)
            releaser <- IO.delay(meet(barrier)).flatMap(_ => sem.release).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => releaser.cancel).start
            _ <- canceler.join
            queued <- sem.waiting
            _ <- if queued == 1 then sem.release else IO.unit
            o <- waiter.join
          yield o
        assertEquals(io.unsafeRun(scheduler, 5.seconds), Outcome.Succeeded(Outcome.Succeeded(())))
    }
  end for

  private def untilAvailable(sem: Semaphore, n: Int): IO[Unit] =
    sem.available.flatMap: seen =>
      if seen == n then IO.unit else cede.flatMap(_ => untilAvailable(sem, n))
end Exercise03Spec
