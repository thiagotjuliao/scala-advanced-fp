package advfp.ch05ioasadescription

/** Exercise 03 — Write programs that run a description more than once.
  * *Solved.*
  *
  * All three hold on to `io`, a description, and use it more than once. That
  * is the whole content of referential transparency put to work: `io` names
  * the program, not its result, so `io.flatMap(a => io.map(b => (a, b)))` runs
  * it twice. With an impure `readLine()` in its place, `val line = readLine();
  * (line, line)` reads once, and no function that receives `line` can make it
  * read again.
  *
  * `retry` counts down in the handler: `io.handleErrorWith(_ => retry(io,
  * attempts - 1))`, and `io` alone when one attempt is left, so that the last
  * error is not caught and is the program's. The recursive call is inside the
  * handler, so it happens when the program runs and the handler is called — one
  * `HandleErrorWith` built per failure, not a million built up front. `require`
  * runs when `retry` is called, before any description exists: a bad argument
  * is the caller's bug, not an effect of the program.
  *
  * `repeatUntil` is the same shape with `flatMap`: run `io`, and if the value
  * will not do, the function given to `flatMap` returns `repeatUntil` again.
  * Neither grows the stack, because neither recursive call is made by the
  * function that built the program. Building the rest of `retry` first —
  * `val rest = retry(io, attempts - 1)`, then `io.handleErrorWith(_ => rest)` —
  * describes the same program and recurses `attempts` deep before anything
  * runs: it passes every test but the one with a million attempts, which
  * overflows.
  */
object Exercise03:

  def twice[A](io: IO[A]): IO[(A, A)] =
    io.flatMap(a => io.map(b => (a, b)))

  def retry[A](io: IO[A], attempts: Int): IO[A] =
    require(attempts >= 1, s"retry needs at least one attempt, got $attempts")
    if attempts == 1 then io else io.handleErrorWith(_ => retry(io, attempts - 1))

  def repeatUntil[A](io: IO[A])(p: A => Boolean): IO[A] =
    io.flatMap(a => if p(a) then IO.pure(a) else repeatUntil(io)(p))
end Exercise03
