package advfp.ch07concurrentprimitives

import advfp.core.unsolved

/** Exercise 04 — Write a bounded `Queue`, whose `offer` waits when it is full.
  *
  * `Ref` and `Deferred` are the ones in `Primitives.scala`, given. As in exercise
  * 03: no lock, no blocking, no state outside the `Ref`.
  *
  *   - `Queue.bounded[A](capacity)`: a program that makes a new, empty one. A
  *     capacity below 1 is rejected with `IllegalArgumentException` where it is
  *     written.
  *   - `offer(a)`: add a value. If the queue is full, wait until there is room,
  *     so a producer can never get ahead of its consumer by more than `capacity`.
  *   - `take`: remove the oldest value. If the queue is empty, wait for one.
  *   - `tryOffer(a)`: add a value if there is room and produce `true`; otherwise
  *     change nothing and produce `false`. It never waits.
  *   - `size`: the values held now.
  *
  * Values come out in the order they went in. Producers held back go in, and
  * consumers waiting are served, in the order they arrived. All of it runs on
  * `Scheduler.pool(1)`.
  *
  * A cancel must lose no value and invent none. A producer canceled while held
  * back has offered nothing. A producer canceled just after it was let in has
  * offered its value, and it stays. A consumer canceled while waiting takes
  * nothing. A consumer canceled just after being handed a value must not lose it:
  * it goes to the next consumer waiting, or back to the front of the queue. That
  * may hold one value more than `capacity`, for a moment, and it is the only way
  * to exceed it.
  *
  * Hint: exercise 03's machine, with three things in the state: the values, the
  * consumers waiting and the producers held back, each with a `Deferred` of its
  * own. A taker's `Deferred` carries the value.
  */
object Exercise04:

  trait Queue[A]:
    def offer(a: A): IO[Unit]
    def take: IO[A]
    def tryOffer(a: A): IO[Boolean]
    def size: IO[Int]

  object Queue:
    def bounded[A](capacity: Int): IO[Queue[A]] = unsolved
end Exercise04
