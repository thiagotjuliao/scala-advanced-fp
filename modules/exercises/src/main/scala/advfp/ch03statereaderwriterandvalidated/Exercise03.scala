package advfp.ch03statereaderwriterandvalidated

/** Exercise 03 — Validate a form and report every error at once.
  *
  * `Validated[E, A]` is either a `Valid` value or the `Invalid` errors that
  * kept it from being one.
  *
  *   - `validatedApplicative`: `pure` and `ap`, for any error type with a
  *     `Semigroup`. Two `Valid`s apply; an `Invalid` on one side is the result;
  *     an `Invalid` on both sides combines the two, the function's errors
  *     first.
  *   - `validateName`: the name with the spaces around it trimmed, or
  *     `"name is empty"` if nothing is left.
  *   - `validateAge`: the age as an `Int`, or `"age is not a number"`, or
  *     `"age must be at least 18"`.
  *   - `validateEmail`: the address as given, or `"email has no @"` when it has
  *     none.
  *   - `validateSignup`: a `Signup` when all three fields are valid, and
  *     otherwise every error from every field, in the order of the fields.
  *     Combine the three validations with the applicative — no matching on
  *     them by hand.
  *
  * Hint: `Signup` has a constructor of three arguments. Chapter 02 applied a
  * function of three arguments to three values in a context with one `pure`
  * and three `ap`s.
  */
object Exercise03:

  enum Validated[+E, +A]:
    case Valid(value: A)
    case Invalid(errors: E)

  import Validated.*

  /** A validation whose errors are messages. */
  type Checked[A] = Validated[NonEmptyList[String], A]

  // Constructors that return the full type. `Validated.Valid(a)` alone is a
  // `Valid[Nothing, A]`, and the applicative is never found for an error type
  // of `Nothing`; see the pitfalls in the theory document.
  def valid[A](a: A): Checked[A] = Validated.Valid(a)

  def invalid[A](error: String): Checked[A] = Validated.Invalid(NonEmptyList.of(error))

  given validatedApplicative: [E: Semigroup] => Applicative[[A] =>> Validated[E, A]] =
    new Applicative[[A] =>> Validated[E, A]]:

      override def pure[A](a: A): Validated[E, A] = Valid(a)

      extension [A](fa: Validated[E, A])
        override def map[B](f: A => B): Validated[E, B] =
          fa match
            case Valid(a) => Valid(f(a))
            case Invalid(e) => Invalid(e)

      extension [A, B](ff: Validated[E, A => B])
        override def ap(fa: Validated[E, A]): Validated[E, B] =
          (ff, fa) match
            case (Valid(f), Valid(a)) => Valid(f(a))
            case (Valid(_), Invalid(e)) => Invalid(e)
            case (Invalid(e), Valid(_)) => Invalid(e)
            case (Invalid(e1), Invalid(e2)) => Invalid(e1 |+| e2)

  final case class Signup(name: String, age: Int, email: String)

  def validateName(raw: String): Checked[String] =
    raw.trim() match
      case "" => Invalid(NonEmptyList.of("name is empty"))
      case s => Valid(s)

  def validateAge(raw: String): Checked[Int] =
    raw.trim().toIntOption match
      case Some(age) if age < 18 =>
        Invalid(NonEmptyList.of("age must be at least 18"))
      case Some(age) => Valid(age)
      case None => Invalid(NonEmptyList.of("age is not a number"))

  def validateEmail(raw: String): Checked[String] =
    raw.trim().find(_ == '@') match
      case Some(_) => Valid(raw)
      case None => Invalid(NonEmptyList.of("email has no @"))

  def validateSignup(name: String, age: String, email: String): Checked[Signup] =
    val ff: Checked[String => Int => String => Signup] =
      valid(Signup.apply.curried)

    ff.ap(validateName(name))
      .ap(validateAge(age))
      .ap(validateEmail(email))

end Exercise03
