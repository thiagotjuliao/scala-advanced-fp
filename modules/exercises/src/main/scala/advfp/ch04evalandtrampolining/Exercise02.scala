package advfp.ch04evalandtrampolining

import advfp.core.unsolved

/** Exercise 02 — Write `Eval`: strict, lazy and memoized values, stack-safe
  * to compose.
  *
  * An `Eval[A]` describes how to compute an `A`, and `.value` computes it.
  * The four ways to make one say *when*:
  *
  *   - `now(a)`: `a` is computed already, like a `val`;
  *   - `later(a)`: computed on the first `.value` and remembered, like a
  *     `lazy val` — a second `.value` does not compute it again;
  *   - `always(a)`: computed on every `.value`, like a `def`;
  *   - `defer(fa)`: `fa` itself is built only on `.value`.
  *
  * `map` and `flatMap` compute nothing either: they describe the next step.
  * And `.value` runs a million nested `map`s, `flatMap`s or `defer`s, nested
  * to the left or to the right, without overflowing.
  *
  * The representation is yours: replace the bodies below, and add the
  * subclasses of `Eval` you need in this file. `evalMonad` is `pure` and
  * `flatMap`, in terms of the methods above.
  *
  * Hint: this is exercise 01's trampoline with more kinds of leaf. Which of
  * the four constructors are leaves, and which one is a `More`? Remembering a
  * `later` needs one `lazy val`.
  */
object Exercise02:

  sealed abstract class Eval[+A]:
    def value: A = unsolved

    def flatMap[B](f: A => Eval[B]): Eval[B] = unsolved

    def map[B](f: A => B): Eval[B] = unsolved

  object Eval:
    def now[A](a: A): Eval[A] = unsolved

    def later[A](a: => A): Eval[A] = unsolved

    def always[A](a: => A): Eval[A] = unsolved

    def defer[A](eval: => Eval[A]): Eval[A] = unsolved

  given evalMonad: Monad[Eval] = unsolved
end Exercise02
