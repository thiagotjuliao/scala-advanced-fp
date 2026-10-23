package advfp.ch06fibersandschedulers

/** Exercise 03 — Write `joinValue`, and `both`: two programs run at the same
  * time. *Solved.*
  *
  * `joinValue` folds the three outcomes back into one program: a value is
  * `pure`, an error is `raiseError`, and `Canceled` has to become *something*,
  * since the joiner was not canceled and must go on or fail. It fails, with an
  * error of its own: a value of type `A` cannot be made up, and the joiner's
  * handlers are the right place to decide what a canceled dependency means.
  * That is the difference between the joiner and the joined — the fiber that
  * was canceled runs no handler, the one that waited for it may.
  *
  * `both` starts both fibers before it waits for either. Starting `b` only
  * after joining `a` describes the same pair of values and runs them one after
  * the other: the meeting test, where each waits for the other, then fails —
  * `a` waits two seconds for `b`, which has not started. Measured 2026-10-23:
  * `Succeeded((false,true))` instead of `Succeeded((true,true))`, under both
  * schedulers.
  *
  * When `a` fails, `b` is canceled before the error is raised, and `cancel`
  * waits until `b` has stopped. Raising without canceling leaves `b` running
  * after `both` has returned — a fiber nobody holds a handle to any more, which
  * nothing will ever stop. The spec counts its steps after `both` ends.
  *
  * This `both` waits for `a` first, so an error in `b` is only seen once `a`
  * has ended: if `a` takes a minute and `b` fails at once, `both` takes a
  * minute to fail. Failing as soon as either does needs to wait for whichever
  * ends first, which is `racePair` — exercise 04.
  */
object Exercise03:

  final class FiberCanceled extends RuntimeException("the fiber was canceled")

  extension [A](fiber: Fiber[A])
    def joinValue: IO[A] =
      fiber.join.flatMap:
        case Outcome.Succeeded(a) => IO.pure(a)
        case Outcome.Errored(e) => IO.raiseError(e)
        case Outcome.Canceled => IO.raiseError(FiberCanceled())

  def both[A, B](a: IO[A], b: IO[B]): IO[(A, B)] =
    for
      fa <- a.start
      fb <- b.start
      first <- fa.join
      pair <- first match
        case Outcome.Succeeded(x) => fb.joinValue.map(y => (x, y))
        case Outcome.Errored(e) => fb.cancel.flatMap(_ => IO.raiseError(e))
        case Outcome.Canceled => fb.cancel.flatMap(_ => IO.raiseError(FiberCanceled()))
    yield pair
end Exercise03
