package advfp.ch04evalandtrampolining

/** Exercise 01 — Write a trampoline, and run a deep mutual recursion on it.
  *
  * A `Trampoline[A]` describes a computation of an `A` as data, one step at a
  * time, so that a loop can run it without a stack frame per step:
  *
  *   - `Done(a)`: finished, with `a`;
  *   - `More(next)`: one step to take — call `next` for the rest;
  *   - `FlatMap(source, f)`: run `source`, then feed its value to `f` and run
  *     what that returns.
  *
  * Write:
  *
  *   - `run`: the loop. It must run a million steps of any shape — a million
  *     `More`s, a million `FlatMap`s nested to the left (what `foldLeft` over
  *     `flatMap` builds) and to the right — in constant stack. Annotate it
  *     with `@tailrec` (from `scala.annotation`), and let the compiler tell
  *     you when it is not a loop.
  *   - `trampolineMonad`: `pure` and `flatMap`. Neither runs anything.
  *   - `isEven` and `isOdd`, for `n >= 0`, each defined through the other the
  *     way the plain recursive version is — and not overflowing at a million.
  *
  * Hint: in `run`, a `FlatMap` whose source is itself a `FlatMap` is the hard
  * case. Running the source first is not a tail call. Which monad law turns
  * `FlatMap(FlatMap(s, g), f)` into a `FlatMap` whose source is smaller?
  */
object Exercise01:

  enum Trampoline[+A]:
    case Done(a: A)
    case More(next: () => Trampoline[A])
    case FlatMap[X, +A](source: Trampoline[X], f: X => Trampoline[A]) extends Trampoline[A]

  import Trampoline.*

  extension [A](t: Trampoline[A])
    @annotation.tailrec
    def run: A =
      t match
        case Done(a) => a
        case More(next) => next().run
        case FlatMap(source, f) =>
          source match
            case Done(a) => f(a).run
            case More(next) => FlatMap(next(), f).run
            case FlatMap(source2, g) =>
              FlatMap(source2, x2 => FlatMap(g(x2), f)).run

  given trampolineMonad: Monad[Trampoline] =
    new Monad[Trampoline]:
      override def pure[A](a: A): Trampoline[A] = Done(a)

      extension [A](fa: Trampoline[A])
        override def flatMap[B](f: A => Trampoline[B]): Trampoline[B] =
          FlatMap(fa, f)

      extension [A](fa: Trampoline[A])
        override def map[B](f: A => B): Trampoline[B] =
          fa.flatMap(a => pure(f(a)))

      extension [A, B](ff: Trampoline[A => B])
        override def ap(fa: Trampoline[A]): Trampoline[B] =
          ff.flatMap(f => fa.map(f))

  def isEven(n: Int): Trampoline[Boolean] =
    require(n >= 0, "n must be non-negative")
    if n == 0 then Done(true)
    else More(() => isOdd(n - 1))

  def isOdd(n: Int): Trampoline[Boolean] =
    require(n >= 0, "n must be non-negative")
    if n == 0 then Done(false)
    else More(() => isEven(n - 1))

end Exercise01
