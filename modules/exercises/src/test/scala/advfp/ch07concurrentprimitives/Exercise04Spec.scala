package advfp.ch07concurrentprimitives

import advfp.core.CourseSuite

import java.util.concurrent.{CyclicBarrier, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import Exercise04.*

class Exercise04Spec extends CourseSuite:

  private val onePool = Scheduler.pool(1)

  private val schedulers = List("pool(4)" -> Scheduler.pool(4), "virtual" -> Scheduler.virtual())

  override def afterAll(): Unit =
    onePool.shutdown()
    schedulers.foreach((_, scheduler) => scheduler.shutdown())

  private def meet(barrier: CyclicBarrier): Unit =
    val _ = barrier.await(5, TimeUnit.SECONDS)

  private def untilSize[A](queue: Queue[A], n: Int): IO[Unit] =
    queue.size.flatMap: seen =>
      if seen == n then IO.unit else IO.sleep(1.milli).flatMap(_ => untilSize(queue, n))

  // A cancel of a fiber not yet waiting would not test the waiting: this gives it
  // time to get there. A fiber late to it fails nothing, it only tests less.
  private val settle = IO.sleep(30.millis)

  // A few turns at the back of the scheduler's line, for a fiber just started to
  // reach its wait, with no timer: a race repeated thousands of times cannot
  // afford `settle`. A fiber late to it tests a little less in that round.
  private val cede: IO[Unit] = IO.async(callback => callback(Right(())))

  private val reachWait: IO[Unit] =
    (1 to 20).foldLeft(IO.unit)((acc, _) => acc.flatMap(_ => cede))

  private def takeAll[A](queue: Queue[A], n: Int): IO[List[A]] =
    if n == 0 then IO.pure(Nil)
    else queue.take.flatMap(a => takeAll(queue, n - 1).map(a :: _))

  private def offerAll[A](queue: Queue[A], as: List[A]): IO[Unit] =
    as match
      case Nil => IO.unit
      case a :: rest => queue.offer(a).flatMap(_ => offerAll(queue, rest))

  private val rounds = 2_000

  test("a capacity below one is rejected where it is written") {
    val _ = intercept[IllegalArgumentException](Queue.bounded[Int](0))
  }

  // --- on a single thread: every order below is forced --------------------------

  test("pool(1): a producer waiting on a full queue does not stop the consumer") {
    val io =
      for
        q <- Queue.bounded[Int](1)
        producer <- offerAll(q, List(1, 2, 3)).start
        values <- takeAll(q, 3)
        _ <- producer.join
      yield values
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(List(1, 2, 3)))
  }

  test("pool(1): a consumer waiting on an empty queue does not stop the producer") {
    val io =
      for
        q <- Queue.bounded[Int](1)
        consumer <- takeAll(q, 3).start
        _ <- offerAll(q, List(1, 2, 3))
        values <- consumer.join
      yield values
    assertEquals(
      io.unsafeRun(onePool, 3.seconds),
      Outcome.Succeeded(Outcome.Succeeded(List(1, 2, 3)))
    )
  }

  test("pool(1): a consumer canceled just after being handed a value does not lose it") {
    // The offer hands the value to the waiting consumer and queues it on the only
    // thread, and this fiber keeps that thread until it joins, so the cancel
    // always lands before the consumer has run again.
    val io =
      for
        q <- Queue.bounded[Int](1)
        consumer <- q.take.start
        _ <- settle
        _ <- q.offer(7)
        _ <- consumer.cancel
        o <- consumer.join
        value <- q.take
      yield (o, value)
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded((Outcome.Canceled, 7)))
  }

  test("pool(1): a value put back goes to the next consumer waiting, in front of the others") {
    val io =
      for
        q <- Queue.bounded[Int](2)
        doomed <- q.take.start
        _ <- settle
        next <- q.take.start
        _ <- settle
        _ <- q.offer(7)
        _ <- doomed.cancel
        o <- next.join
      yield o
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded(Outcome.Succeeded(7)))
  }

  test(
    "pool(1): a value put back goes in front, the one way past capacity, and only for a moment"
  ) {
    val io =
      for
        q <- Queue.bounded[Int](1)
        doomed <- q.take.start
        _ <- settle
        _ <- q.offer(1)
        _ <- q.offer(2)
        _ <- doomed.cancel
        over <- q.size
        held <- q.offer(3).start
        _ <- settle
        stillOver <- q.size
        first <- q.take
        afterFirst <- q.size
        second <- q.take
        third <- q.take
        _ <- held.join
      yield (over, stillOver, afterFirst, List(first, second, third))
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded((2, 2, 1, List(1, 2, 3))))
  }

  test("pool(1): a producer canceled just after being let in has still offered its value") {
    val io =
      for
        q <- Queue.bounded[Int](1)
        _ <- q.offer(1)
        producer <- q.offer(2).start
        _ <- settle
        first <- q.take
        _ <- producer.cancel
        second <- q.take
      yield (first, second)
    assertEquals(io.unsafeRun(onePool, 3.seconds), Outcome.Succeeded((1, 2)))
  }

  for (name, scheduler) <- schedulers do
    def run[A](io: IO[A]): Outcome[A] = io.unsafeRun(scheduler, 20.seconds)

    test(s"$name: values come out in the order they went in") {
      val io =
        for
          q <- Queue.bounded[Int](3)
          _ <- offerAll(q, List(1, 2, 3))
          values <- takeAll(q, 3)
        yield values
      assertEquals(run(io), Outcome.Succeeded(List(1, 2, 3)))
    }

    test(s"$name: size counts the values held") {
      val io =
        for
          q <- Queue.bounded[Int](2)
          a <- q.size
          _ <- q.offer(1)
          b <- q.size
          _ <- q.take
          c <- q.size
        yield (a, b, c)
      assertEquals(run(io), Outcome.Succeeded((0, 1, 0)))
    }

    test(s"$name: nothing happens until the program runs") {
      val io =
        for
          q <- Queue.bounded[Int](1)
          unused = q.offer(1)
          before <- q.size
          _ <- unused
          after <- q.size
        yield (before, after)
      assertEquals(run(io), Outcome.Succeeded((0, 1)))
    }

    test(s"$name: tryOffer says whether the value went in, and never waits") {
      val io =
        for
          q <- Queue.bounded[Int](1)
          a <- q.tryOffer(1)
          b <- q.tryOffer(2)
          value <- q.take
          size <- q.size
        yield (a, b, value, size)
      assertEquals(run(io), Outcome.Succeeded((true, false, 1, 0)))
    }

    test(s"$name: tryOffer goes straight to a consumer that is waiting") {
      val io =
        for
          q <- Queue.bounded[Int](1)
          consumer <- q.take.start
          _ <- settle
          accepted <- q.tryOffer(1)
          o <- consumer.join
          size <- q.size
        yield (accepted, o, size)
      assertEquals(run(io), Outcome.Succeeded((true, Outcome.Succeeded(1), 0)))
    }

    test(s"$name: a full queue holds the producer back until a value is taken") {
      val offered = AtomicInteger(0)
      // Waits for the count, then settles so that a count going too far shows: a
      // producer late to it only makes this take longer.
      def countAt(n: Int): IO[Int] =
        IO.delay(offered.get).flatMap: seen =>
          if seen >= n then settle.flatMap(_ => IO.delay(offered.get))
          else IO.sleep(1.milli).flatMap(_ => countAt(n))
      val io =
        for
          q <- Queue.bounded[Int](2)
          producer <- (0 until 5).toList
            .foldLeft(IO.unit)((acc, i) =>
              acc
                .flatMap(_ => q.offer(i))
                .flatMap(_ => IO.delay(offered.incrementAndGet()).map(_ => ()))
            )
            .start
          _ <- untilSize(q, 2)
          heldAt <- countAt(2)
          held <- q.size
          _ <- q.take
          next <- countAt(3)
          _ <- producer.cancel
        yield (heldAt, held, next)
      assertEquals(run(io), Outcome.Succeeded((2, 2, 3)))
    }

    test(s"$name: producers held back go in, in the order they arrived") {
      val io =
        for
          q <- Queue.bounded[Int](1)
          _ <- q.offer(0)
          a <- q.offer(1).start
          _ <- settle
          b <- q.offer(2).start
          _ <- settle
          values <- takeAll(q, 3)
          _ <- a.join
          _ <- b.join
          size <- q.size
        yield (values, size)
      assertEquals(run(io), Outcome.Succeeded((List(0, 1, 2), 0)))
    }

    test(s"$name: consumers waiting are served in the order they arrived") {
      val io =
        for
          q <- Queue.bounded[Int](2)
          a <- q.take.start
          _ <- settle
          b <- q.take.start
          _ <- settle
          _ <- q.offer(1)
          _ <- q.offer(2)
          ra <- a.join
          rb <- b.join
        yield (ra, rb)
      assertEquals(run(io), Outcome.Succeeded((Outcome.Succeeded(1), Outcome.Succeeded(2))))
    }

    test(s"$name: four producers and four consumers deliver every value exactly once") {
      val io =
        for
          q <- Queue.bounded[Int](4)
          producers <- (0 until 4).foldLeft(IO.pure(List.empty[Fiber[Unit]])): (acc, p) =>
            acc.flatMap(fs =>
              offerAll(q, (0 until 2000).map(i => p * 10000 + i).toList).start.map(_ :: fs)
            )
          consumers <- (0 until 4).foldLeft(IO.pure(List.empty[Fiber[List[Int]]])): (acc, _) =>
            acc.flatMap(fs => takeAll(q, 2000).start.map(_ :: fs))
          _ <- producers.foldLeft(IO.unit)((acc, f) => acc.flatMap(_ => f.join.map(_ => ())))
          values <- consumers.foldLeft(IO.pure(List.empty[Int])): (acc, f) =>
            acc.flatMap: all =>
              f.join.flatMap:
                case Outcome.Succeeded(vs) => IO.pure(vs ++ all)
                case other => IO.raiseError(AssertionError(s"a consumer ended with $other"))
        yield values
      val expected = (0 until 4).flatMap(p => (0 until 2000).map(i => p * 10000 + i)).toSet
      run(io) match
        case Outcome.Succeeded(values) =>
          assertEquals(values.size, 8000)
          assertEquals(values.toSet, expected)
        case other => fail(s"expected every value to arrive: $other")
    }

    test(s"$name: a canceled producer's value never goes in") {
      val io =
        for
          q <- Queue.bounded[Int](1)
          _ <- q.offer(1)
          producer <- q.offer(2).start
          _ <- settle
          _ <- producer.cancel
          o <- producer.join
          value <- q.take
          size <- q.size
        yield (o, value, size)
      assertEquals(run(io), Outcome.Succeeded((Outcome.Canceled, 1, 0)))
    }

    test(s"$name: a canceled consumer takes nothing: the next value goes to whoever is next") {
      val io =
        for
          q <- Queue.bounded[Int](1)
          doomed <- q.take.start
          _ <- settle
          next <- q.take.start
          _ <- settle
          _ <- doomed.cancel
          _ <- q.offer(1)
          o <- next.join
        yield o
      assertEquals(run(io), Outcome.Succeeded(Outcome.Succeeded(1)))
    }

    test(s"$name: an offer canceled halfway never leaves a consumer waiting for nothing") {
      // Either the offer did nothing and the consumer still waits, so the next
      // value reaches it, or it took the consumer off the queue, and then it must
      // have handed it the value.
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            q <- Queue.bounded[Int](2)
            consumer <- q.take.start
            _ <- reachWait
            offerer <- IO.delay(meet(barrier)).flatMap(_ => q.offer(1)).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => offerer.cancel).start
            _ <- canceler.join
            _ <- q.offer(2)
            o <- consumer.join
          yield o
        io.unsafeRun(scheduler, 5.seconds) match
          case Outcome.Succeeded(Outcome.Succeeded(_)) => ()
          case other => fail(s"the consumer did not end: $other")
    }

    test(s"$name: a tryOffer canceled halfway never leaves a consumer waiting for nothing") {
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            q <- Queue.bounded[Int](2)
            consumer <- q.take.start
            _ <- reachWait
            offerer <- IO.delay(meet(barrier)).flatMap(_ => q.tryOffer(1)).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => offerer.cancel).start
            _ <- canceler.join
            _ <- q.offer(2)
            o <- consumer.join
          yield o
        io.unsafeRun(scheduler, 5.seconds) match
          case Outcome.Succeeded(Outcome.Succeeded(_)) => ()
          case other => fail(s"the consumer did not end: $other")
    }

    test(s"$name: a cancel that races a producer on its way to wait leaves no value behind") {
      // Nothing is taken until the producer has ended, so it was never let in: a
      // canceled offer has offered nothing, wherever the cancel landed.
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            q <- Queue.bounded[Int](1)
            _ <- q.offer(1)
            producer <- IO.delay(meet(barrier)).flatMap(_ => q.offer(2)).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => producer.cancel).start
            _ <- canceler.join
            o <- producer.join
            first <- q.take
            left <- q.size
          yield (o, first, left)
        assertEquals(run(io), Outcome.Succeeded((Outcome.Canceled, 1, 0)))
    }

    test(s"$name: a cancel that races a take loses no value") {
      (1 to rounds).foreach: _ =>
        val barrier = CyclicBarrier(2)
        val io =
          for
            q <- Queue.bounded[Int](1)
            consumer <- q.take.start
            offerer <- IO.delay(meet(barrier)).flatMap(_ => q.offer(1)).start
            canceler <- IO.delay(meet(barrier)).flatMap(_ => consumer.cancel).start
            _ <- offerer.join
            _ <- canceler.join
            o <- consumer.join
            left <- q.size
          yield (o, left)
        run(io) match
          case Outcome.Succeeded((Outcome.Succeeded(1), 0)) => ()
          case Outcome.Succeeded((Outcome.Canceled, 1)) => ()
          case other => fail(s"the value was lost or duplicated: $other")
    }
  end for
end Exercise04Spec
