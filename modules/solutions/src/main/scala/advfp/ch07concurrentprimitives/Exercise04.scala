package advfp.ch07concurrentprimitives

import scala.collection.immutable.Queue as Fifo

/** Exercise 04 — Write a bounded `Queue`, whose `offer` waits when it is full.
  * *Solved.*
  *
  * Exercise 03's machine with more in the state: the values, the consumers
  * waiting, and the producers held back with the value each one wants to add. Two
  * invariants hold it together: a consumer waits only if there are no values, and
  * a producer is held back only if the values are at capacity.
  *
  * A value never passes through the buffer when someone is waiting for it: `offer`
  * hands it straight to the first consumer. And when a `take` makes room, it lets
  * the first producer in within the same `modify`, so a late `offer` cannot slip
  * into the space ahead of it.
  *
  * Each waiting side has a finalizer, and they are not symmetric, because the two
  * hand-offs carry different things. A producer let in has already had its value
  * moved into the buffer, so when canceled it only leaves the queue, if it is
  * still there; the offer has happened, and a canceled fiber does not undo it. A
  * consumer handed a value is the one holding it, and if it is canceled now that
  * value is in a `Deferred` nobody will read. So its finalizer reads it, and puts
  * it back: to the next consumer, or at the front of the buffer, since it was the
  * oldest. Measured 2026-11-05: a finalizer that only leaves the queue fails three
  * tests on `pool(1)` and the race under each scheduler.
  *
  * Everything between a `modify` and what it produced runs inside a region, the
  * three that can hand a consumer a value included. In `offer` without one, a
  * cancel can land after the `modify` has taken the consumer off the queue and
  * before the `complete`, and the consumer waits for a value that is gone.
  *
  * Putting a value back can push the buffer one past `capacity`, if producers
  * filled it in the meantime. The alternative is to block a finalizer on a full
  * queue, and a finalizer that waits for others is how a cancel hangs; a moment
  * over capacity is the smaller promise to break, and `take` lets no producer in
  * until it is under again.
  */
object Exercise04:

  trait Queue[A]:
    def offer(a: A): IO[Unit]
    def take: IO[A]
    def tryOffer(a: A): IO[Boolean]
    def size: IO[Int]

  object Queue:
    def bounded[A](capacity: Int): IO[Queue[A]] =
      require(capacity >= 1, s"a queue needs room for at least one value, got $capacity")
      Ref.of(State[A](Fifo.empty, Fifo.empty, Fifo.empty)).map(Machine(capacity, _))

  private final case class State[A](
      values: Fifo[A],
      consumers: Fifo[Deferred[A]],
      producers: Fifo[(A, Deferred[Unit])]
  )

  private final class Machine[A](capacity: Int, state: Ref[State[A]]) extends Queue[A]:

    def offer(a: A): IO[Unit] =
      IO.uncancelable: poll =>
        Deferred[Unit].flatMap: admitted =>
          state
            .modify: s =>
              s.consumers.dequeueOption match
                case Some((first, rest)) =>
                  (s.copy(consumers = rest), first.complete(a).map(_ => ()))
                case None if s.values.size < capacity =>
                  (s.copy(values = s.values.enqueue(a)), IO.unit)
                case None =>
                  val held = s.copy(producers = s.producers.enqueue((a, admitted)))
                  (held, poll(admitted.get).onCancel(leaveProducers(admitted)))
            .flatMap(identity)

    def take: IO[A] =
      IO.uncancelable: poll =>
        Deferred[A].flatMap: slot =>
          state
            .modify: s =>
              s.values.dequeueOption match
                case Some((a, rest)) => admitNext(s.copy(values = rest), a)
                case None =>
                  val waiting = s.copy(consumers = s.consumers.enqueue(slot))
                  (waiting, poll(slot.get).onCancel(leaveConsumers(slot)))
            .flatMap(identity)

    def tryOffer(a: A): IO[Boolean] =
      IO.uncancelable: _ =>
        state
          .modify: s =>
            s.consumers.dequeueOption match
              case Some((first, rest)) =>
                (s.copy(consumers = rest), first.complete(a).map(_ => true))
              case None if s.values.size < capacity =>
                (s.copy(values = s.values.enqueue(a)), IO.pure(true))
              case None => (s, IO.pure(false))
          .flatMap(identity)

    def size: IO[Int] = state.get.map(_.values.size)

    // A value just left: if that made room, the first producer held back comes in.
    private def admitNext(s: State[A], taken: A): (State[A], IO[A]) =
      s.producers.dequeueOption match
        case Some(((value, admitted), rest)) if s.values.size < capacity =>
          val next = s.copy(values = s.values.enqueue(value), producers = rest)
          (next, admitted.complete(()).map(_ => taken))
        case _ => (s, IO.pure(taken))

    // Not there means it was let in: its value is in the queue, and stays.
    private def leaveProducers(admitted: Deferred[Unit]): IO[Unit] =
      state.update(s => s.copy(producers = s.producers.filterNot((_, d) => d eq admitted)))

    // Not there means it was handed a value, which is on its way to `slot`.
    private def leaveConsumers(slot: Deferred[A]): IO[Unit] =
      state
        .modify: s =>
          if s.consumers.exists(_ eq slot) then
            (s.copy(consumers = s.consumers.filterNot(_ eq slot)), IO.unit)
          else (s, slot.get.flatMap(putBack))
        .flatMap(identity)

    private def putBack(a: A): IO[Unit] =
      state
        .modify: s =>
          s.consumers.dequeueOption match
            case Some((first, rest)) => (s.copy(consumers = rest), first.complete(a).map(_ => ()))
            case None => (s.copy(values = a +: s.values), IO.unit)
        .flatMap(identity)
  end Machine
end Exercise04
