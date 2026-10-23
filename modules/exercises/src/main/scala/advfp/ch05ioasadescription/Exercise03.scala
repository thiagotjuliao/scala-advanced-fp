package advfp.ch05ioasadescription

/** Exercise 03 — Write programs that run a description more than once.
  *
  * Each of these takes a program and runs it again — something no function
  * can do with a value that has already been computed. The `IO` is the one in
  * `IO.scala`, given; none of these needs `unsafeRun()`, and each must build a
  * description without running anything.
  *
  *   - `twice(io)`: run `io`, then run it again, and pair the two values. If
  *     the first run fails, there is no second.
  *   - `retry(io, attempts)`: run `io`; if it fails, run it again, up to
  *     `attempts` runs in total. The result is the first success, or the error
  *     of the last run. `attempts` must be at least 1 — reject anything less
  *     with `require`, when `retry` is called.
  *   - `repeatUntil(io)(p)`: run `io` until it produces a value that satisfies
  *     `p`, and return that value. An error stops it.
  *
  * `retry` and `repeatUntil` must survive a million runs.
  *
  * Hint: each is recursive, and the recursive call belongs inside the function
  * given to `flatMap` or `handleErrorWith` — there it runs when the program
  * runs, one step at a time, in `IO`'s own loop. A recursive call made while
  * *building* the program builds a million programs before any of them runs.
  */
object Exercise03:

  def twice[A](io: IO[A]): IO[(A, A)] =
    for
      a1 <- io
      a2 <- io
    yield (a1, a2)

  def retry[A](io: IO[A], attempts: Int): IO[A] =
    require(attempts >= 1, s"retry needs at least one attempt, got $attempts")

    if attempts == 1 then io
    else io.handleErrorWith(_ => retry(io, attempts - 1))

  def repeatUntil[A](io: IO[A])(p: A => Boolean): IO[A] =
    io.flatMap: a =>
      if p(a) then IO.pure(a)
      else repeatUntil(io)(p)
end Exercise03
