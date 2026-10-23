package advfp.ch05ioasadescription

/** Exercise 02 — Write `attempt`, `rethrow`, `handleError` and `guarantee` on
  * `IO`'s primitives.
  *
  * The `IO` is the one in `IO.scala`, given. Use only what it offers —
  * `IO.pure`, `IO.raiseError`, `flatMap`, `map`, `handleErrorWith` — and none
  * of these needs `unsafeRun()`.
  *
  *   - `attempt`: a program that never fails; it produces `Right(a)` when this
  *     one produces `a`, and `Left(e)` when this one fails with `e`.
  *   - `rethrow`: the other way round — `Left(e)` becomes the error `e`.
  *     `io.attempt.rethrow` behaves exactly like `io`.
  *   - `handleError`: like `handleErrorWith`, with a handler that returns a
  *     plain value.
  *   - `guarantee(finalizer)`: run this program, then `finalizer`, whether this
  *     program succeeded or failed, and exactly once. The result is this
  *     program's — its value, or its error. A finalizer that fails after a
  *     success makes the whole program fail with the finalizer's error; a
  *     finalizer that fails after a failure does not hide the original error,
  *     which is the one the program fails with.
  *
  * Hint: `guarantee` written with `flatMap` for the success and
  * `handleErrorWith` for the failure has a trap: the handler also sees the
  * finalizer's own error. `attempt` first, and decide afterwards.
  */
object Exercise02:

  extension [A](io: IO[A])
    def attempt: IO[Either[Throwable, A]] =
      io.map(Right(_)).handleErrorWith(e => IO.pure(Left(e)))

    def handleError[B >: A](f: Throwable => B): IO[B] =
      io.handleErrorWith(e => IO.delay(f(e)))

    def guarantee(finalizer: IO[Unit]): IO[A] =
      io.attempt.flatMap:
        case Left(e) => finalizer.attempt.flatMap(_ => IO.raiseError(e))
        case Right(a) => finalizer.flatMap(_ => IO.pure(a))

  extension [A](io: IO[Either[Throwable, A]])
    def rethrow: IO[A] =
      io.flatMap:
        case Left(e) => IO.raiseError(e)
        case Right(a) => IO.pure(a)
end Exercise02
