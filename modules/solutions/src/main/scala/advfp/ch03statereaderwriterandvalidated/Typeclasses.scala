package advfp.ch03statereaderwriterandvalidated

/** The chapter's typeclasses, and the one data type its exercises share: given,
  * not exercises.
  *
  * The same file sits in `exercises` and in `solutions`, so a stub and its
  * answer program against identical definitions. The hierarchy is redeclared
  * here rather than imported from chapter 02, so this chapter can be studied
  * on its own.
  *
  * One thing changed since chapter 02: `Monad` derives `ap` and `map` from
  * `flatMap`. Chapter 02 had every instance write `ap` by hand, so that the
  * agreement law was something to satisfy; here it holds by construction, and
  * exercise 04 is about the one type that cannot have it.
  */

/** A type constructor whose contents can be transformed in place.
  *
  * Laws — identity: `fa.map(a => a) == fa`; composition:
  * `fa.map(f).map(g) == fa.map(f andThen g)`.
  */
trait Functor[F[_]]:
  extension [A](fa: F[A]) def map[B](f: A => B): F[B]

object Functor:
  def apply[F[_]](using functor: Functor[F]): Functor[F] = functor

/** A functor that can lift a plain value, and apply a function that is itself
  * inside the context.
  *
  * Laws:
  *   - identity: `pure(identity).ap(v) == v`;
  *   - homomorphism: `pure(f).ap(pure(x)) == pure(f(x))`;
  *   - interchange: `u.ap(pure(y)) == pure((f: A => B) => f(y)).ap(u)`;
  *   - composition: `pure(compose).ap(u).ap(v).ap(w) == u.ap(v.ap(w))`.
  */
trait Applicative[F[_]] extends Functor[F]:
  def pure[A](a: A): F[A]

  extension [A, B](ff: F[A => B]) def ap(fa: F[A]): F[B]

  extension [A](fa: F[A]) override def map[B](f: A => B): F[B] = pure(f).ap(fa)

object Applicative:
  def apply[F[_]](using applicative: Applicative[F]): Applicative[F] = applicative

/** An applicative whose next step can depend on the value the previous one
  * produced.
  *
  * `ap` and `map` are derived from `flatMap` and `pure`, so the agreement law,
  * `ff.ap(fa) == ff.flatMap(f => fa.map(f))`, holds for every instance unless
  * one overrides `ap` with something else. `map` is overridden too: the
  * `Applicative` default goes through `ap`, which here goes through `map`, and
  * the two defaults would call each other forever.
  *
  * Laws, besides the applicative ones:
  *   - left identity: `pure(a).flatMap(f) == f(a)`;
  *   - right identity: `fa.flatMap(pure) == fa`;
  *   - associativity: `fa.flatMap(f).flatMap(g) == fa.flatMap(a => f(a).flatMap(g))`.
  */
trait Monad[F[_]] extends Applicative[F]:
  extension [A](fa: F[A]) def flatMap[B](f: A => F[B]): F[B]

  extension [A](fa: F[A]) override def map[B](f: A => B): F[B] = fa.flatMap(a => pure(f(a)))

  extension [A, B](ff: F[A => B]) def ap(fa: F[A]): F[B] = ff.flatMap(f => fa.map(f))

object Monad:
  def apply[F[_]](using monad: Monad[F]): Monad[F] = monad

/** A type with an associative way to combine two values.
  *
  * Law — associativity: `(x |+| y) |+| z == x |+| (y |+| z)`.
  */
trait Semigroup[A]:
  extension (x: A) def |+|(y: A): A

object Semigroup:
  def apply[A](using semigroup: Semigroup[A]): Semigroup[A] = semigroup

/** A semigroup with a neutral element.
  *
  * Laws, besides associativity: `empty |+| x == x` and `x |+| empty == x`.
  */
trait Monoid[A] extends Semigroup[A]:
  def empty: A

object Monoid:
  def apply[A](using monoid: Monoid[A]): Monoid[A] = monoid

  /** Concatenation, the left operand first: the log of what happened first
    * stays in front.
    */
  given listMonoid: [A] => Monoid[List[A]]:
    def empty: List[A] = Nil
    extension (x: List[A]) def |+|(y: List[A]): List[A] = x ++ y

/** A list with at least one element.
  *
  * The errors of a failed validation: an `Invalid` with no errors in it would
  * be a failure that cannot say what failed, and this type makes it
  * unrepresentable. There is a `Semigroup` and, on purpose, no `Monoid`: an
  * empty value is exactly what it cannot have.
  */
final case class NonEmptyList[+A](head: A, tail: List[A]):
  def toList: List[A] = head :: tail

  def ++[B >: A](that: NonEmptyList[B]): NonEmptyList[B] =
    NonEmptyList(head, tail ++ that.toList)

object NonEmptyList:
  def of[A](head: A, tail: A*): NonEmptyList[A] = NonEmptyList(head, tail.toList)

  given semigroup: [A] => Semigroup[NonEmptyList[A]]:
    extension (x: NonEmptyList[A]) def |+|(y: NonEmptyList[A]): NonEmptyList[A] = x ++ y
