package advfp.ch05ioasadescription

/** Exercise 02 — Write `attempt`, `rethrow`, `handleError` and `guarantee` on
  * `IO`'s primitives. *Solved.*
  *
  * `attempt` is `map(Right(_))` with a handler that turns the error into a
  * `Left`, so both outcomes come out as a value and the program cannot fail.
  * `rethrow` is its inverse, one `flatMap` that turns a
  * `Left` back into `IO.raiseError`. `handleError` is `handleErrorWith` with
  * the result wrapped in `IO.pure` — inside the handler, so an exception the
  * function throws becomes the program's error, as everywhere else in `IO`.
  *
  * `guarantee` is where the obvious version goes wrong:
  *
  * {{{
  * io.flatMap(a => finalizer.map(_ => a))
  *   .handleErrorWith(e => finalizer.flatMap(_ => IO.raiseError(e)))
  * }}}
  *
  * The handler wraps the finalizer of the success path as well, so a finalizer
  * that fails after a success is caught by it and *run a second time*. The fix
  * is to stop error handling at the program's edge before the finalizer runs:
  * `attempt` the program, so that what comes out is a value either way, run
  * the finalizer once, and only then decide. After a success, the finalizer's
  * own error is the result — nothing else failed. After a failure, the
  * finalizer is attempted too, and its error dropped, so it cannot hide the
  * error that made the program fail, which is the one that says what went
  * wrong. `cats-effect` makes the same choice.
  */
object Exercise02:

  extension [A](io: IO[A])
    def attempt: IO[Either[Throwable, A]] =
      io.map(Right(_)).handleErrorWith(e => IO.pure(Left(e)))

    def handleError[B >: A](f: Throwable => B): IO[B] =
      io.handleErrorWith(e => IO.pure(f(e)))

    def guarantee(finalizer: IO[Unit]): IO[A] =
      io.attempt.flatMap:
        case Right(a) => finalizer.map(_ => a)
        case Left(e) => finalizer.attempt.flatMap(_ => IO.raiseError(e))

  extension [A](io: IO[Either[Throwable, A]])
    def rethrow: IO[A] =
      io.flatMap:
        case Right(a) => IO.pure(a)
        case Left(e) => IO.raiseError(e)
end Exercise02
