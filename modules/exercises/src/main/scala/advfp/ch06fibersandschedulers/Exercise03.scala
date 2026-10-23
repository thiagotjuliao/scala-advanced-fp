package advfp.ch06fibersandschedulers

import advfp.core.unsolved

/** Exercise 03 — Write `joinValue`, and `both`: two programs run at the same
  * time.
  *
  * The `IO`, `Fiber` and `Outcome` are the ones in `IO.scala`, given. Use what
  * they offer — `start`, `join`, `cancel`, `flatMap`, `IO.pure`,
  * `IO.raiseError` — and none of these needs `unsafeRun`.
  *
  *   - `fiber.joinValue`: wait for the fiber and produce its value. A fiber that
  *     failed makes this program fail with the same error; a fiber that was
  *     canceled makes it fail with `FiberCanceled`.
  *   - `both(a, b)`: run `a` and `b` at the same time, and pair their values.
  *     If `a` fails, `b` is canceled — it must not go on running after `both`
  *     has ended — and `both` fails with `a`'s error. If `a` succeeds and `b`
  *     fails, `both` fails with `b`'s error.
  *
  * Hint: an `Outcome` has three cases and `joinValue` decides all three. In
  * `both`, what has to have happened to `b` before `a` is waited for?
  */
object Exercise03:

  final class FiberCanceled extends RuntimeException("the fiber was canceled")

  extension [A](fiber: Fiber[A]) def joinValue: IO[A] = unsolved

  def both[A, B](a: IO[A], b: IO[B]): IO[(A, B)] = unsolved
end Exercise03
