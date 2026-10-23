package advfp.ch05ioasadescription

import scala.util.control.NonFatal

/** Exercise 01 — Write the run loop of `IO`: stack-safe, and with errors.
  *
  * An `IO[A]` describes a program that produces an `A` or fails:
  *
  *   - `Pure(a)`: a value, no effect;
  *   - `Delay(thunk)`: call `thunk` when the program runs;
  *   - `RaiseError(e)`: fail with `e`;
  *   - `FlatMap(source, f)`: run `source`, feed its value to `f`, run what `f`
  *     returns;
  *   - `HandleErrorWith(source, handler)`: run `source`, and if *it* fails with
  *     `e`, run `handler(e)` instead.
  *
  * Write:
  *
  *   - `flatMap`, `map` and `handleErrorWith`, which build descriptions and run
  *     nothing;
  *   - `unsafeRun()`, which runs the program, every time it is called, and
  *     returns its value in `Right` or its error in `Left`.
  *
  * `unsafeRun()` must:
  *
  *   - turn an exception thrown by a `Delay`'s thunk, by a function given to
  *     `flatMap` or `map`, or by a handler into the program's error, instead of
  *     letting it escape (`scala.util.control.NonFatal` says which exceptions a
  *     program should catch);
  *   - give a handler only the errors raised inside its own `source`: once the
  *     `source` has succeeded, an error raised by a later `flatMap` goes past
  *     it, to the next handler out;
  *   - run a million `flatMap`s nested to the left or to the right, a million
  *     nested handlers, and an error unwinding through a million `flatMap`s,
  *     without overflowing. Annotate it, or the loop inside it, with `@tailrec`.
  *
  * Hint: chapter 04's trampoline rewrote `FlatMap(FlatMap(s, g), f)` into a
  * smaller `FlatMap`. A handler cannot be re-associated that way: it has to
  * stay around until its `source` is finished. Keep what is left to do in a
  * stack of your own — an immutable `List` is a stack — holding two kinds of
  * entries. When a value comes out, what happens to each kind? And when an
  * error does? A `try` around a recursive call is not a tail call: catch in a
  * helper, outside the loop.
  */
object Exercise01:

  enum IO[+A]:
    case Pure(a: A)
    case Delay(thunk: () => A)
    case RaiseError(error: Throwable)
    case FlatMap[X, +A](source: IO[X], f: X => IO[A]) extends IO[A]
    case HandleErrorWith(source: IO[A], handler: Throwable => IO[A])

  import IO.*

  extension [A](io: IO[A])
    def flatMap[B](f: A => IO[B]): IO[B] = FlatMap(io, f)

    def map[B](f: A => B): IO[B] =
      val g: A => IO[B] = a => Delay(() => f(a))
      FlatMap(io, g)

    def handleErrorWith[B >: A](handler: Throwable => IO[B]): IO[B] =
      HandleErrorWith(io, handler)

    def unsafeRun(): Either[Throwable, A] =
      type StepFunction = Either[Throwable, Any] => IO[Any]

      def guard(io: => IO[Any]): IO[Any] =
        try io
        catch case NonFatal(e) => RaiseError(e)

      @annotation.tailrec
      def loop(source: IO[Any], stack: List[StepFunction]): Either[Throwable, Any] =
        source match
          case Pure(a) =>
            stack match
              case Nil => Right(a)
              case sf :: t => loop(guard(sf(Right(a))), t)

          case FlatMap(current, f) =>
            // `f` takes the hidden `X`, and `X => IO[A]` is not an
            // `Any => IO[Any]`: a function's input is contravariant. The cast is
            // safe because this frame is only ever popped with the value
            // `current` produces, which is an `X`.
            val g: StepFunction =
              case Right(a) => f.asInstanceOf[Any => IO[Any]](a)
              case Left(e) => RaiseError(e)
            loop(current, g :: stack)

          case RaiseError(e) =>
            stack match
              case Nil => Left(e)
              case sf :: t => loop(guard(sf(Left(e))), t)

          case Delay(thunk) =>
            loop(guard(Pure(thunk())), stack)

          case HandleErrorWith(current, h) =>
            val g: StepFunction =
              case Right(a) => Pure(a)
              case Left(e) => h(e)
            loop(current, g :: stack)

      // The loop runs `IO`s of every intermediate type, so it works on `Any`.
      // What leaves it with an empty stack is the value of the whole program,
      // `io`, which is an `A`.
      loop(io, List()).asInstanceOf[Either[Throwable, A]]
    end unsafeRun
  end extension
end Exercise01
