package advfp.ch07concurrentprimitives

import scala.collection.immutable.Queue as Fifo

/** Exercise 03 — Write `Semaphore` from `Ref` and `Deferred`: fair, and safe to
  * cancel at any moment. *Solved.*
  *
  * The state is the permits and the queue, and one invariant holds it together:
  * someone is queued only if no permit is free. Every operation is one `modify`
  * that decides from the state what happens, and produces the rest of the program
  * as a value, which runs after the `modify` has settled it.
  *
  * `release` hands its permit over instead of adding it: with someone queued the
  * count stays at 0, so a fiber arriving now finds nothing and queues behind. A
  * version that adds a permit and wakes the waiter is shorter, and lets a fast
  * fiber overtake it forever.
  *
  * Cancellation is three separate holes, and each near miss below opens one.
  * Measured 2026-11-05, each against this spec:
  *
  *   - `acquire` without `uncancelable`: a cancel can land on the bind between
  *     the `modify` that joins the queue and the `get`, and the fiber stays queued
  *     with nobody waiting there. The next release hands a permit to it, and the
  *     permit is gone.
  *   - A finalizer that only leaves the queue: a fiber already handed a permit
  *     is no longer queued, so nothing gives that permit back. The finalizer has
  *     to tell the two apart, and it asks the one that knows, the state: still
  *     queued means it holds nothing; gone means it holds a permit, and releases.
  *   - `release` without `uncancelable`: a bind sits between the `modify` that
  *     takes the waiter off the queue and the `complete` that wakes it. A cancel
  *     there leaves a fiber waiting for a `complete` that will never run.
  *
  * `withPermit` is what a program should use. A plain `acquire` that has returned
  * has given up its protection: the next bind is a cancellation point, and a
  * canceled fiber holding a permit has nobody to release it. Inside `withPermit`
  * the mask is on from the moment the permit is taken until `poll(io)`, and the
  * finalizer is in place before `poll` lets a cancel through.
  */
object Exercise03:

  trait Semaphore:
    def acquire: IO[Unit]
    def release: IO[Unit]
    def available: IO[Int]
    def waiting: IO[Int]
    def withPermit[A](io: IO[A]): IO[A]

  object Semaphore:
    def apply(permits: Int): IO[Semaphore] =
      require(permits >= 0, s"a semaphore cannot start with $permits permits")
      Ref.of(State(permits, Fifo.empty)).map(Machine(_))

  private final case class State(permits: Int, queue: Fifo[Deferred[Unit]])

  private final class Machine(state: Ref[State]) extends Semaphore:

    def acquire: IO[Unit] =
      IO.uncancelable: poll =>
        Deferred[Unit].flatMap: mine =>
          state
            .modify: s =>
              if s.permits > 0 then (s.copy(permits = s.permits - 1), IO.unit)
              else (s.copy(queue = s.queue.enqueue(mine)), poll(mine.get).onCancel(leave(mine)))
            .flatMap(identity)

    def release: IO[Unit] =
      IO.uncancelable: _ =>
        state
          .modify: s =>
            s.queue.dequeueOption match
              case Some((first, rest)) => (s.copy(queue = rest), first.complete(()).map(_ => ()))
              case None => (s.copy(permits = s.permits + 1), IO.unit)
          .flatMap(identity)

    def available: IO[Int] = state.get.map(_.permits)

    def waiting: IO[Int] = state.get.map(_.queue.size)

    def withPermit[A](io: IO[A]): IO[A] =
      IO.uncancelable: poll =>
        for
          _ <- poll(acquire)
          a <- poll(io)
            .onCancel(release)
            .handleErrorWith(e => release.flatMap(_ => IO.raiseError(e)))
          _ <- release
        yield a

    private def leave(mine: Deferred[Unit]): IO[Unit] =
      state
        .modify: s =>
          if s.queue.exists(_ eq mine) then (s.copy(queue = s.queue.filterNot(_ eq mine)), IO.unit)
          else (s, release)
        .flatMap(identity)
  end Machine
end Exercise03
