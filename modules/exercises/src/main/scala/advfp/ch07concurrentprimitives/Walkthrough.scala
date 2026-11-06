package advfp.ch07concurrentprimitives

import scala.collection.immutable.Queue as Fifo
import scala.concurrent.duration.*

/** Chapter 07 — Concurrent primitives.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch07-concurrent-primitives.md`.
  */
object Walkthrough:

  // --- 1. A cell fibers share ---------------------------------------------------
  //
  // `Ref.of` is a program: a cell is made when it runs, and running it twice makes
  // two. Whoever shares the `Ref` shares the cell, and that is visible in the
  // program, not hidden in a global.

  val countTwice: IO[Int] =
    for
      rows <- Ref.of(0)
      a <- rows.update(_ + 1).start
      b <- rows.update(_ + 1).start
      _ <- a.join
      _ <- b.join
      total <- rows.get
    yield total

  // --- 2. `modify`: one read, one write, and an answer from the same read -------
  //
  // Reading with `get` and then writing with `set` leaves a gap another fiber can
  // run in. `modify` retries the whole step, so its function may run more than
  // once: keep it pure.

  def nextTicket(counter: Ref[Int]): IO[Int] = counter.modify(n => (n + 1, n))

  // --- 3. A value that arrives once ---------------------------------------------
  //
  // `get` suspends and holds no thread. `complete` lets everyone go on, and says
  // whether it was the one that filled it.

  val signal: IO[(Int, Boolean)] =
    for
      schema <- Deferred[Int]
      writer <- schema.get.map(_ + 1).start
      _ <- IO.sleep(5.millis)
      first <- schema.complete(41)
      outcome <- writer.join
      value <- outcome match
        case Outcome.Succeeded(n) => IO.pure(n)
        case _ => IO.pure(0)
    yield (value, first)

  // --- 4. A state machine in a `Ref`, with its waiters as `Deferred`s -----------
  //
  // A latch that opens after `n` countdowns. Every step is one `modify`, which
  // decides from the state and produces the rest of the program as a value: the
  // state changes atomically, and the waking happens afterwards, once.

  final class Latch private (state: Ref[Int], opened: Deferred[Unit]):
    def await: IO[Unit] = opened.get

    def countDown: IO[Unit] =
      state
        .modify: left =>
          if left == 1 then (0, opened.complete(()).map(_ => ()))
          else (math.max(left - 1, 0), IO.unit)
        .flatMap(identity)

  object Latch:
    def apply(n: Int): IO[Latch] =
      for
        state <- Ref.of(n)
        opened <- Deferred[Unit]
      yield new Latch(state, opened)

  // --- 5. Regions and finalizers ------------------------------------------------
  //
  // `uncancelable` turns cancellation off, `poll` turns it back on for the one part
  // that may wait, and `onCancel` says what to undo if the fiber stops there. The
  // pattern below is what every primitive of the chapter is built on: take the
  // thing where nothing can interrupt, wait where something can, and undo it on
  // the way out.

  def guarded[A](take: IO[Unit], give: IO[Unit])(use: IO[A]): IO[A] =
    IO.uncancelable: poll =>
      for
        _ <- take
        a <- poll(use)
          .onCancel(give)
          .handleErrorWith(e => give.flatMap(_ => IO.raiseError(e)))
        _ <- give
      yield a

  // --- 6. Joining a queue, and leaving it ---------------------------------------
  //
  // A "turnstile": the machine of exercise 03 with one permit and the cancellation
  // left out, to show the shape. Exercise 03 is what this costs when a cancel may
  // land anywhere, and it asks what is wrong with this version.

  final class Turnstile private (state: Ref[(Boolean, Fifo[Deferred[Unit]])]):
    def enter: IO[Unit] =
      Deferred[Unit].flatMap: mine =>
        state
          .modify:
            case (false, queue) => ((true, queue), IO.unit)
            case (true, queue) => ((true, queue.enqueue(mine)), mine.get)
          .flatMap(identity)

    def leave: IO[Unit] =
      state
        .modify:
          case (_, queue) =>
            queue.dequeueOption match
              case Some((first, rest)) => ((true, rest), first.complete(()).map(_ => ()))
              case None => ((false, queue), IO.unit)
        .flatMap(identity)

  object Turnstile:
    val make: IO[Turnstile] = Ref.of((false, Fifo.empty[Deferred[Unit]])).map(new Turnstile(_))

  // --- 7. Back-pressure ---------------------------------------------------------
  //
  // A bounded queue's `offer` waits when the queue is full, so the producer can
  // never be more than `capacity` ahead. With the consumer gone it stops, which is
  // the point: the alternative is a producer that fills memory until the JVM dies.
  // Exercise 04 writes it; this is the shape of what uses it.

  def pipeline[A](
      read: IO[Option[A]],
      offer: A => IO[Unit],
      take: IO[A],
      write: A => IO[Unit]
  ): IO[Fiber[Unit]] =
    def produce: IO[Unit] =
      read.flatMap:
        case Some(a) => offer(a).flatMap(_ => produce)
        case None => IO.unit
    def consume: IO[Unit] = take.flatMap(write).flatMap(_ => consume)
    for
      consumer <- consume.start
      producer <- produce.flatMap(_ => consumer.cancel).start
    yield producer
end Walkthrough
