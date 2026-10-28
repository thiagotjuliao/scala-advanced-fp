package advfp.ch06fibersandschedulers

import Outcome.*

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

  extension [A](fiber: Fiber[A])
    def joinValue: IO[A] =
      fiber.join.flatMap:
        case Succeeded(value) => IO.pure(value)
        case Errored(error) => IO.raiseError(error)
        case Canceled => IO.raiseError(FiberCanceled())

  def both[A, B](a: IO[A], b: IO[B]): IO[(A, B)] =
    for
      fa <- a.start
      fb <- b.start
      oa <- fa.join
      res <-
        oa match
          case Succeeded(a) =>
            fb.joinValue.map(b => (a, b))
          case Errored(error) =>
            fb.cancel.flatMap(_ => IO.raiseError(error))
          case Canceled =>
            fb.cancel.flatMap(_ => IO.raiseError(FiberCanceled()))

    yield res

end Exercise03
