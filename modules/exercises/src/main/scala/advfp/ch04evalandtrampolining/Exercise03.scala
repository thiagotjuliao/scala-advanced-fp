package advfp.ch04evalandtrampolining

import advfp.core.unsolved

/** Exercise 03 — Fold a long list from the right, lazily.
  *
  * The `Eval` used here is the one in `Eval.scala`: given, complete, and
  * stack-safe. This exercise is about using it.
  *
  *   - `foldRight`: `f(a1, f(a2, ... f(an, z)))`, with the rest of the fold
  *     handed to `f` as an `Eval[B]` it may or may not look at. `f` is called
  *     on the first element first, and on the next one only when the `Eval` it
  *     was handed is run. A million elements must not overflow — not while
  *     the fold is built, and not while it runs.
  *   - `exists`: whether some element satisfies `p`, written with
  *     `foldRight`. It stops at the first match: `p` is never called on an
  *     element after it.
  *
  * Hint: the recursive call for the tail must not happen while `foldRight` is
  * building its result. Which `Eval` constructor postpones building one?
  */
object Exercise03:

  def foldRight[A, B](as: List[A], z: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = unsolved

  def exists[A](as: List[A])(p: A => Boolean): Eval[Boolean] = unsolved
end Exercise03
