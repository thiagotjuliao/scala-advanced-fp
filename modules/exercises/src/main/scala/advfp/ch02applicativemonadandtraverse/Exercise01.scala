package advfp.ch02applicativemonadandtraverse

import advfp.core.unsolved

/** Exercise 01 — Write `Monad` for `Option`, `Either[E, *]` and `List`.
  *
  * Each instance writes `pure`, `ap` and `flatMap` — `Monad` here does not
  * derive `ap` for you. That is the point: three operations, and laws that tie
  * them together.
  *
  *   - `Option`: a missing value anywhere makes the result missing.
  *   - `Either[E, *]`: a `Left` anywhere makes the result that `Left`. When both
  *     sides of `ap` are `Left`s, the function's wins, because it comes first.
  *   - `List`: every function applied to every value, the functions on the
  *     outside — `List(f, g).ap(List(x, y))` is `List(f(x), f(y), g(x), g(y))`.
  *     `flatMap` concatenates.
  *
  * The spec checks the applicative laws, the monad laws, and the one that
  * binds them: `ff.ap(fa) == ff.flatMap(f => fa.map(f))`.
  *
  * Hint: write `flatMap` first, then ask what `ap` has to be so that it cannot
  * be told apart from `ff.flatMap(f => fa.map(f))`.
  */
object Exercise01:

  given optionMonad: Monad[Option] = unsolved

  given eitherMonad: [E] => Monad[[A] =>> Either[E, A]] = unsolved

  given listMonad: Monad[List] = unsolved
