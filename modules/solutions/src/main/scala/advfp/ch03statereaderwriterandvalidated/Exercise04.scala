package advfp.ch03statereaderwriterandvalidated

/** Exercise 04 — Show that `Validated` cannot be a monad. *Solved.*
  *
  * `flatMap` has no choice. On `Valid(a)` it is `f(a)`. On `Invalid(e)` there
  * is no `A` to call `f` with, and the only `Validated[E, B]` within reach is
  * `Invalid(e)` itself. That is `Either`'s `flatMap`, and it obeys the three
  * monad laws, as the spec checks: nothing is wrong with it in isolation.
  *
  * The derived `ap` is `ff.flatMap(f => fa.map(f))`. When `ff` is `Invalid`,
  * `flatMap` returns it without looking at `fa`, so `fa`'s errors are lost.
  * The accumulating `ap` looks at both. They agree whenever one side is
  * `Valid` — with a `Valid` function both return `fa` mapped, and with a valid
  * value both return the function's errors — so the counterexample needs two
  * `Invalid`s. Any two will do: `Invalid([f])` against `Invalid([v])` is
  * `Invalid([f, v])` one way and `Invalid([f])` the other.
  *
  * So a type gets to choose: accumulate, and have `ap` without a lawful
  * `flatMap`; or short-circuit, and have a monad. Not both in one instance,
  * because code generic in `Monad[F]` may use either and must get the same
  * answer. Code that needs one validation to depend on another converts that
  * step to `Either` (or uses a `flatMap` that is not part of any instance) and
  * gives up accumulation at that step only.
  */
object Exercise04:

  enum Validated[+E, +A]:
    case Valid(value: A)
    case Invalid(errors: E)

  /** A validation whose errors are messages. */
  type Checked[A] = Validated[NonEmptyList[String], A]

  def valid[A](a: A): Checked[A] = Validated.Valid(a)

  def invalid[A](error: String): Checked[A] = Validated.Invalid(NonEmptyList.of(error))

  given validatedApplicative: [E: Semigroup] => Applicative[[A] =>> Validated[E, A]]:
    def pure[A](a: A): Validated[E, A] = Validated.Valid(a)

    extension [A, B](ff: Validated[E, A => B])
      def ap(fa: Validated[E, A]): Validated[E, B] =
        (ff, fa) match
          case (Validated.Valid(f), Validated.Valid(a)) => Validated.Valid(f(a))
          case (Validated.Invalid(e1), Validated.Invalid(e2)) => Validated.Invalid(e1 |+| e2)
          case (Validated.Invalid(e), _) => Validated.Invalid(e)
          case (_, Validated.Invalid(e)) => Validated.Invalid(e)

  extension [E, A](fa: Validated[E, A])
    def flatMap[B](f: A => Validated[E, B]): Validated[E, B] =
      fa match
        case Validated.Valid(a) => f(a)
        case Validated.Invalid(e) => Validated.Invalid(e)

  /** The `ap` a monad built on this `flatMap` would have to have — the
    * agreement law read as a definition. Given, not yours.
    */
  def apFromFlatMap[A, B](ff: Checked[A => B])(fa: Checked[A]): Checked[B] =
    ff.flatMap(f => fa.map(f))

  def counterexample: (Checked[Int => Int], Checked[Int]) =
    (invalid("f"), invalid("v"))
end Exercise04
