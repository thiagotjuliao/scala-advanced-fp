package advfp.ch03statereaderwriterandvalidated

/** Exercise 03 — Validate a form and report every error at once. *Solved.*
  *
  * `ap` matches on both sides at once, which is the difference from `Either`:
  * an `Either`'s `ap`, derived from `flatMap`, looks at the function first and
  * never at the value if the function failed. Here the two-`Invalid` case is
  * its own branch and combines with `|+|`, the function's errors on the left
  * so that the errors come out in the order the arguments were written.
  *
  * Keeping only one side's errors in that branch — either side — passes all
  * four applicative laws: keeping the function's is `Either`'s applicative,
  * lawful and different. The laws say an `ap` is *an* applicative, not which
  * one; only the tests with two `Invalid`s say this one accumulates. Checked
  * 2026-10-16 by running the spec against both versions: the laws stayed
  * green, the two accumulation tests went red.
  *
  * `pure` is `Valid`. It needs no error value, which is why `E` only needs a
  * `Semigroup` and why `NonEmptyList` — which has no `Monoid` — works.
  *
  * `validateSignup` lifts the curried constructor with `pure` and applies it
  * to the three validations. Each `ap` adds one field's errors after the
  * previous ones, so the order of the arguments is the order of the messages.
  * None of the three validations depends on another: that independence is
  * what makes `ap` enough, and what makes accumulating possible at all.
  */
object Exercise03:

  enum Validated[+E, +A]:
    case Valid(value: A)
    case Invalid(errors: E)

  /** A validation whose errors are messages. */
  type Checked[A] = Validated[NonEmptyList[String], A]

  // Constructors that return the full type. `Validated.Valid(a)` alone is a
  // `Valid[Nothing, A]`, and the applicative is never found for an error type
  // of `Nothing`; see the pitfalls in the theory document.
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

  final case class Signup(name: String, age: Int, email: String)

  def validateName(raw: String): Checked[String] =
    val name = raw.trim
    if name.isEmpty then invalid("name is empty") else valid(name)

  def validateAge(raw: String): Checked[Int] =
    raw.toIntOption match
      case None => invalid("age is not a number")
      case Some(age) if age < 18 => invalid("age must be at least 18")
      case Some(age) => valid(age)

  def validateEmail(raw: String): Checked[String] =
    if raw.contains('@') then valid(raw) else invalid("email has no @")

  def validateSignup(name: String, age: String, email: String): Checked[Signup] =
    Applicative[Checked]
      .pure((n: String) => (a: Int) => (e: String) => Signup(n, a, e))
      .ap(validateName(name))
      .ap(validateAge(age))
      .ap(validateEmail(email))
end Exercise03
