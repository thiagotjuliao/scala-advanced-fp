package advfp.ch04evalandtrampolining

import scala.annotation.tailrec

/** Exercise 01 — Write a trampoline, and run a deep mutual recursion on it.
  * *Solved.*
  *
  * `run` is one `@tailrec` method, so it is a loop: every case either returns
  * or calls `run` again as its last act. `Done` returns. `More` takes one
  * step and loops. A `FlatMap` whose source is a leaf (`Done`, or a `More`
  * after one step) feeds the value to `f` and loops on the result.
  *
  * The case that matters is a `FlatMap` whose source is another `FlatMap`.
  * The obvious code runs the source first — `run(f(run(source)))` — and that
  * inner `run` is not in tail position: `@tailrec` rejects it, and without the
  * annotation it compiles, passes every small test, and overflows on a
  * left-nested chain, which is what `foldLeft` over `flatMap` builds. Instead
  * the loop re-associates, `FlatMap(FlatMap(s, g), f)` into
  * `FlatMap(s, x => FlatMap(g(x), f))` — the associativity law, used as a
  * rewrite. Each rewrite peels one level off the left, so a chain of any depth
  * is walked with a constant stack. It is the same program, and the law is
  * what says so.
  *
  * `isEven` and `isOdd` return `More(() => ...)` instead of calling each
  * other. Building `isEven(1_000_000)` is one object; the million steps happen
  * inside `run`. Calling `isOdd(n - 1)` directly, without `More`, has the
  * right type and is the original stack overflow again, now at construction
  * time.
  */
object Exercise01:

  enum Trampoline[+A]:
    case Done(a: A)
    case More(next: () => Trampoline[A])
    case FlatMap[X, +A](source: Trampoline[X], f: X => Trampoline[A]) extends Trampoline[A]

  import Trampoline.*

  extension [A](t: Trampoline[A])
    @tailrec def run: A =
      t match
        case Done(a) => a
        case More(next) => next().run
        case FlatMap(source, f) =>
          source match
            case Done(x) => f(x).run
            case More(next) => FlatMap(next(), f).run
            case FlatMap(inner, g) => FlatMap(inner, x => FlatMap(g(x), f)).run

  given trampolineMonad: Monad[Trampoline]:
    def pure[A](a: A): Trampoline[A] = Done(a)

    extension [A](fa: Trampoline[A])
      def flatMap[B](f: A => Trampoline[B]): Trampoline[B] = FlatMap(fa, f)

  def isEven(n: Int): Trampoline[Boolean] =
    if n == 0 then Done(true) else More(() => isOdd(n - 1))

  def isOdd(n: Int): Trampoline[Boolean] =
    if n == 0 then Done(false) else More(() => isEven(n - 1))
end Exercise01
