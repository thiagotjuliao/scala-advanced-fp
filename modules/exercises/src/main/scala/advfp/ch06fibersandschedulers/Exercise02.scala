package advfp.ch06fibersandschedulers

import scala.util.control.NonFatal
import scala.annotation.tailrec
import advfp.ch06fibersandschedulers.Outcome.*

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

  enum Stack[-X, +A]:
    case Empty[A]() extends Stack[A, A]
    case Bind[X, E, A](f: X => IO[E], stack: Stack[E, A]) extends Stack[X, A]
    case Handler[X, A](h: Throwable => IO[X], stack: Stack[X, A]) extends Stack[X, A]

  import Stack.*

  extension [A](io: IO[A])
    def flatMap[B](f: A => IO[B]): IO[B] = FlatMap(io, f)

    def map[B](f: A => B): IO[B] = flatMap(a => Pure(f(a)))

    def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] = HandleErrorWith(io, handler)

    def run(isCanceled: () => Boolean): Outcome[A] =
      def guard[A](io: => IO[A]): IO[A] =
        try io
        catch case NonFatal(e) => RaiseError(e)

      @tailrec def loop[X, A](io: IO[X], stack: Stack[X, A]): Outcome[A] =
        io match
          case p @ Pure(a) =>
            stack match
              case Empty() => Succeeded(a)
              case Bind(f, rest) =>
                if isCanceled() then Canceled
                else loop(guard(f(a)), rest)
              case Handler(_, stack) =>
                loop(p, stack)

          case RaiseError(error) =>
            stack match
              case Empty() => Errored(error)
              case Bind(_, rest) => loop(RaiseError(error), rest)
              case Handler(h, rest) =>
                if isCanceled() then Canceled
                else loop(guard(h(error)), rest)

          case Delay(thunk) =>
            loop(guard(Pure(thunk())), stack)

          case FlatMap(source, f) => loop(source, Bind(f, stack))

          case HandleErrorWith(source, handler) => loop(source, Handler(handler, stack))

      loop(io, Empty())
    end run
  end extension
end Exercise02
