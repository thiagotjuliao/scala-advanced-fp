package advfp.ch07concurrentprimitives

import advfp.core.unsolved

/** Exercise 03 — Write `Semaphore` from `Ref` and `Deferred`: fair, and safe to
  * cancel at any moment.
  *
  * `Ref` and `Deferred` are the ones in `Primitives.scala`, given. The `IO` in
  * `IO.scala` has `IO.uncancelable(poll => ...)` and `io.onCancel(finalizer)`.
  * No lock, no blocking, and no state outside the `Ref`.
  *
  *   - `Semaphore(n)`: a program that makes a new one with `n` permits. A
  *     negative `n` is rejected with `IllegalArgumentException` where it is
  *     written, not when the program runs.
  *   - `acquire`: take a permit; if there is none, join the queue and wait.
  *   - `release`: give one back. If someone is waiting it goes directly to the
  *     first in the queue, and `available` does not rise, so fibers go through in
  *     the order they arrived and nobody can overtake.
  *   - `available`, `waiting`: how many permits are free, how many fibers queue.
  *   - `withPermit(io)`: run `io` holding a permit, and give it back however `io`
  *     ends: value, error or cancel.
  *
  * A cancel must lose nothing, wherever it lands. A fiber canceled while waiting
  * leaves the queue. A fiber canceled just after it was handed a permit gives
  * that permit back, since it will never use it. A `release` that was canceled
  * either did nothing or finished: it never takes a fiber off the queue and then
  * stops.
  *
  * Hint: one `modify` decides everything, and produces the rest of the program as
  * a value. Where is the only place a waiting fiber should be cancelable, and how
  * does its finalizer tell whether it is still in the queue?
  */
object Exercise03:

  trait Semaphore:
    def acquire: IO[Unit]
    def release: IO[Unit]
    def available: IO[Int]
    def waiting: IO[Int]
    def withPermit[A](io: IO[A]): IO[A]

  object Semaphore:
    def apply(permits: Int): IO[Semaphore] = unsolved
end Exercise03
