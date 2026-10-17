package advfp.ch04evalandtrampolining

import advfp.core.unsolved

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

  extension [A](t: Trampoline[A]) def run: A = unsolved

  given trampolineMonad: Monad[Trampoline] = unsolved

  def isEven(n: Int): Trampoline[Boolean] = unsolved

  def isOdd(n: Int): Trampoline[Boolean] = unsolved
end Exercise01
