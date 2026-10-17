package advfp.ch04evalandtrampolining

/** Exercise 03 — Fold a long list from the right, lazily. *Solved.*
  *
  * `foldRight` of `a :: rest` is `f(a, Eval.defer(foldRight(rest, z)(f)))`.
  * The `defer` is the whole exercise. Without it, `foldRight(rest, z)(f)` is
  * an argument, evaluated before `f` is called: the fold recurses once per
  * element while it is being *built*, and a million elements overflow before
  * any `Eval` exists to protect them. With it, building the fold is one call
  * to `f`, and each further call happens inside `.value`, in the trampoline,
  * when — and only if — `f` runs the `Eval` it was handed.
  *
  * The same `defer` is what makes the fold lazy. `exists` hands `foldRight` a
  * function that answers `Eval.now(true)` on a match and returns the rest
  * otherwise, so the rest is never run past the first match. A fold written
  * with the standard library's `foldRight` has the right type and gets this
  * wrong: it calls `f` on every element, from the last one, before anything is
  * run, and `p` sees elements after the match.
  *
  * A fold `f` that does use the rest — `acc.map(_ + a)` — still runs in
  * constant stack. Each element adds a `defer` and a `map` to the chain, and
  * the trampoline walks a million of them.
  */
object Exercise03:

  def foldRight[A, B](as: List[A], z: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
    as match
      case Nil => z
      case a :: rest => f(a, Eval.defer(foldRight(rest, z)(f)))

  def exists[A](as: List[A])(p: A => Boolean): Eval[Boolean] =
    foldRight(as, Eval.now(false))((a, rest) => if p(a) then Eval.now(true) else rest)
end Exercise03
