package advfp.ch03statereaderwriterandvalidated

import advfp.core.unsolved

/** Exercise 04 — Show that `Validated` cannot be a monad.
  *
  * A monad's `ap` must agree with its `flatMap`:
  * `ff.ap(fa) == ff.flatMap(f => fa.map(f))`. Give `Validated` both, and find
  * where they part.
  *
  *   - `validatedApplicative`: the accumulating `pure` and `ap`. Exercise 03
  *     wrote it already; it is asked for again so this exercise stands on its
  *     own.
  *   - `flatMap`: the only one `Validated` can have. A `Valid` continues with
  *     its value. An `Invalid` has no value to continue with — what can it
  *     return?
  *   - `counterexample`: a function and a value on which `apFromFlatMap`, the
  *     `ap` that `flatMap` determines, and the accumulating `ap` give different
  *     results.
  *
  * The spec also checks that your `flatMap`, on its own, obeys all three monad
  * laws. Nothing is wrong with it, and nothing is wrong with `ap`; they cannot
  * live in the same instance.
  *
  * Hint: the spec checks that the two agree whenever at least one side is
  * `Valid`. What is left?
  */
object Exercise04:

  enum Validated[+E, +A]:
    case Valid(value: A)
    case Invalid(errors: E)

  /** A validation whose errors are messages. */
  type Checked[A] = Validated[NonEmptyList[String], A]

  def valid[A](a: A): Checked[A] = Validated.Valid(a)

  def invalid[A](error: String): Checked[A] = Validated.Invalid(NonEmptyList.of(error))

  given validatedApplicative: [E: Semigroup] => Applicative[[A] =>> Validated[E, A]] = unsolved

  extension [E, A](fa: Validated[E, A])
    def flatMap[B](f: A => Validated[E, B]): Validated[E, B] =
      unsolved

  /** The `ap` a monad built on this `flatMap` would have to have — the
    * agreement law read as a definition. Given, not yours.
    */
  def apFromFlatMap[A, B](ff: Checked[A => B])(fa: Checked[A]): Checked[B] =
    ff.flatMap(f => fa.map(f))

  def counterexample: (Checked[Int => Int], Checked[Int]) = unsolved
end Exercise04
