package advfp.ch05ioasadescription

import scala.annotation.tailrec
import scala.util.control.NonFatal

/** Exercise 01 — Write the run loop of `IO`: stack-safe, and with errors.
  * *Solved.*
  *
  * `flatMap`, `map` and `handleErrorWith` only build nodes. All the work is in
  * `unsafeRun()`, which is one `@tailrec` loop over two things: the `IO` being
  * run and a stack of what is left to do. The stack is an immutable `List` of
  * two kinds of frame. A `FlatMap` pushes its function and goes on with its
  * source; a `HandleErrorWith` pushes its handler and goes on with its source.
  *
  * When a value comes out — a `Pure`, or a `Delay` once its thunk has run —
  * the loop pops a frame. A function is applied to the value, and the loop
  * goes on with the `IO` it returns. A handler is dropped: its source
  * succeeded, and a handler that stayed on the stack would catch errors raised
  * after it, by `flatMap`s that are not its business. When an error comes out
  * the loop pops too, dropping functions — there is no value to give them —
  * until it finds a handler, and goes on with what the handler returns. An
  * empty stack ends the loop with the value or the error.
  *
  * Chapter 04 re-associated `FlatMap(FlatMap(s, g), f)` instead of keeping a
  * stack. That rewrite has no counterpart for a handler, whose scope must end
  * exactly where its source does; an explicit stack handles both kinds of frame
  * the same way, and pushing a frame is the re-association done once per node.
  * The frames are cells of a `List` on the heap, which is why a million of
  * them are fine where a million JVM frames are not.
  *
  * Every call to code the loop does not control — a thunk, a function, a
  * handler — goes through `protect`, which turns a non-fatal exception into a
  * `RaiseError`. The `try` cannot sit in `loop` itself: a recursive call inside
  * a `try` is not a tail call, and `@tailrec` rejects it. `NonFatal` rather
  * than `Throwable`: a `StackOverflowError` or an `OutOfMemoryError` is not a
  * program's error to handle, and catching an `InterruptedException` would
  * swallow a request to stop.
  *
  * The frames hold functions from `Any`: `FlatMap` hides the type of its
  * source, and a `List` holds one element type. The casts are confined to two
  * places, where a `FlatMap`'s function is pushed and where the final value
  * leaves the loop, and in both the types are known to line up. The
  * `IO` in `IO.scala` does the same job without casts, with a stack whose type
  * tracks what each frame expects — worth reading after this one.
  */
object Exercise01:

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
    def unsafeRun(): Either[Throwable, A] =
      loop(io, Nil).map(_.asInstanceOf[A])

  @tailrec private def loop(io: IO[Any], stack: List[Frame]): Either[Throwable, Any] =
    io match
      case Pure(a) =>
        stack match
          case Nil => Right(a)
          case Bind(f) :: rest => loop(protect(f(a)), rest)
          case Handle(_) :: rest => loop(io, rest)
      case Delay(thunk) => loop(protect(Pure(thunk())), stack)
      case RaiseError(error) =>
        stack match
          case Nil => Left(error)
          case Bind(_) :: rest => loop(io, rest)
          case Handle(handler) :: rest => loop(protect(handler(error)), rest)
      case FlatMap(source, f) =>
        loop(source, Bind(f.asInstanceOf[Any => IO[Any]]) :: stack)
      case HandleErrorWith(source, handler) => loop(source, Handle(handler) :: stack)

  private def protect(io: => IO[Any]): IO[Any] =
    try io
    catch case NonFatal(e) => RaiseError(e)
end Exercise01
