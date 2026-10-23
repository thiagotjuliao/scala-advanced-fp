package advfp.ch06fibersandschedulers

import scala.annotation.tailrec
import scala.util.control.NonFatal

/** Exercise 02 — Write a run loop that ends in an `Outcome`, and stops at its
  * cancellation points. *Solved.*
  *
  * Chapter 05's loop, with the flag read in exactly two places. A value that
  * meets a `Bind` is about to be handed to the next step the program
  * described; an error that meets a `Handle` is about to run a handler. Both
  * are boundaries the description drew — between two steps — and stopping
  * there leaves nothing half done. Everywhere else the loop either pushes a
  * frame, which does nothing, or is inside a thunk, which it cannot stop
  * without interrupting someone's effect halfway.
  *
  * The check goes where the function is *applied*, not where the `FlatMap` is
  * met and its function pushed. Pushing happens before the source runs, so a
  * check there misses a cancel requested by the source itself: `cancel.map(_ =>
  * 1)` pushes with the flag down, runs the cancel, and then applies the
  * function and succeeds. A chain built left to right — `a.flatMap(f).flatMap(g)`
  * — is worse: every `FlatMap` is met, and every function pushed, before the
  * first effect runs, so nothing is ever checked again. Measured 2026-10-23:
  * that version fails five of the eleven tests.
  *
  * A canceled program runs no handler because cancellation is not an error.
  * Handing it to `handleErrorWith` would let the program recover from being
  * asked to stop — and a handler that retries would undo the cancel. The
  * result is the third case of `Outcome`, not a `Left`. Measured 2026-10-23: a
  * loop that checks before binds but not before handlers fails one test, the
  * one that cancels and throws in the same effect.
  *
  * Read the flag once, before the loop starts, and the loop is not cancelable
  * at all: a fiber is canceled while it runs, by someone else, and the flag
  * has to be read again at every point. Measured 2026-10-23: six of the eleven
  * tests fail.
  */
object Exercise02:

  enum IO[+A]:
    case Pure(a: A)
    case Delay(thunk: () => A)
    case RaiseError(error: Throwable)
    case FlatMap[X, +A](source: IO[X], f: X => IO[A]) extends IO[A]
    case HandleErrorWith(source: IO[A], handler: Throwable => IO[A])

  import IO.*

  private enum Frame:
    case Bind(f: Any => IO[Any])
    case Handle(handler: Throwable => IO[Any])

  import Frame.*

  extension [A](io: IO[A])
    def flatMap[B](f: A => IO[B]): IO[B] = FlatMap(io, f)

    def map[B](f: A => B): IO[B] = flatMap(a => Pure(f(a)))

    def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] = HandleErrorWith(io, handler)

    // The loop produces an `Any`; the frames were pushed so that what comes out
    // of an `IO[A]` with an empty stack is an `A`.
    def run(isCanceled: () => Boolean): Outcome[A] =
      loop(io, Nil, isCanceled) match
        case Outcome.Succeeded(a) => Outcome.Succeeded(a.asInstanceOf[A])
        case Outcome.Errored(e) => Outcome.Errored(e)
        case Outcome.Canceled => Outcome.Canceled

  @tailrec private def loop(
      io: IO[Any],
      stack: List[Frame],
      isCanceled: () => Boolean
  ): Outcome[Any] =
    io match
      case Pure(a) =>
        stack match
          case Nil => Outcome.Succeeded(a)
          case Bind(f) :: rest =>
            if isCanceled() then Outcome.Canceled
            else loop(protect(f(a)), rest, isCanceled)
          case Handle(_) :: rest => loop(io, rest, isCanceled)
      case Delay(thunk) => loop(protect(Pure(thunk())), stack, isCanceled)
      case RaiseError(error) =>
        stack match
          case Nil => Outcome.Errored(error)
          case Bind(_) :: rest => loop(io, rest, isCanceled)
          case Handle(handler) :: rest =>
            if isCanceled() then Outcome.Canceled
            else loop(protect(handler(error)), rest, isCanceled)
      case FlatMap(source, f) =>
        loop(source, Bind(f.asInstanceOf[Any => IO[Any]]) :: stack, isCanceled)
      case HandleErrorWith(source, handler) => loop(source, Handle(handler) :: stack, isCanceled)

  private def protect(io: => IO[Any]): IO[Any] =
    try io
    catch case NonFatal(e) => RaiseError(e)
end Exercise02
