package advfp.ch06fibersandschedulers

import advfp.core.unsolved

import scala.concurrent.duration.FiniteDuration

/** Exercise 04 — Write `race`, which cancels the loser, and `timeout` on top of
  * it.
  *
  * The `IO`, `Fiber` and `Outcome` are the ones in `IO.scala`, given; besides
  * `start`, `join` and `cancel`, it offers `IO.racePair(a, b)`, which starts
  * both programs and produces the outcome of the first to end together with
  * the other's fiber, still running, and `IO.sleep(duration)`.
  *
  *   - `race(a, b)`: run both, and produce the value of the one that ends
  *     first, `Left` for `a` and `Right` for `b`. If the first to end failed,
  *     the race fails with its error. Either way, the other program is
  *     canceled, and has stopped, before the race ends: nothing it would have
  *     done afterwards is done. If the first to end was canceled — by someone
  *     outside the race — the race is decided by the other one.
  *   - `io.timeout(duration)`: `io`'s value or error, if it ends within
  *     `duration`; otherwise `io` is stopped, and the program fails with a
  *     `java.util.concurrent.TimeoutException`.
  *
  * Hint: what does `racePair` leave for its caller to do with the second
  * fiber? And for `timeout`, what does a race against a clock produce when the
  * clock wins?
  */
object Exercise04:

  def race[A, B](a: IO[A], b: IO[B]): IO[Either[A, B]] = unsolved

  extension [A](io: IO[A]) def timeout(duration: FiniteDuration): IO[A] = unsolved
end Exercise04
