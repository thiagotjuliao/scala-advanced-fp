package advfp.ch06fibersandschedulers

import advfp.core.unsolved

/** Exercise 02 — Write a run loop that ends in an `Outcome`, and stops at its
  * cancellation points.
  *
  * The `IO` below is chapter 05's algebra, with `flatMap`, `map` and
  * `handleErrorWith` already written. Write `run(isCanceled)`, which runs the
  * program on the calling thread and says how it ended:
  *
  *   - `Succeeded(a)` when it produced `a`;
  *   - `Errored(e)` when it failed with `e` — raised, or thrown by a thunk, a
  *     function or a handler (`scala.util.control.NonFatal` says which to
  *     catch);
  *   - `Canceled` when `isCanceled()` was `true` at one of its cancellation
  *     points.
  *
  * The cancellation points are two: just before the function of a `flatMap`
  * (or a `map`) is applied to a value, and just before a handler is run. A
  * canceled program runs no handler. Nowhere else: an effect that is running
  * is never interrupted, however long it takes, and `isCanceled` may turn
  * `true` while it runs — set by the effect itself, in the spec.
  *
  * Like chapter 05's, the loop must run a million binds, and unwind an error
  * through a million, without overflowing. Annotate it with `@tailrec`.
  *
  * Hint: start from your loop of chapter 05's exercise 01. A value meeting a
  * function on the stack, and an error meeting a handler, are the two places
  * where the loop is about to run code the program described next.
  */
object Exercise02:

  enum IO[+A]:
    case Pure(a: A)
    case Delay(thunk: () => A)
    case RaiseError(error: Throwable)
    case FlatMap[X, +A](source: IO[X], f: X => IO[A]) extends IO[A]
    case HandleErrorWith(source: IO[A], handler: Throwable => IO[A])

  import IO.*

  extension [A](io: IO[A])
    def flatMap[B](f: A => IO[B]): IO[B] = FlatMap(io, f)

    def map[B](f: A => B): IO[B] = flatMap(a => Pure(f(a)))

    def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] = HandleErrorWith(io, handler)

    def run(isCanceled: () => Boolean): Outcome[A] = unsolved
end Exercise02
