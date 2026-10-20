package advfp.ch05ioasadescription

/** The chapter's typeclasses: given, not exercises.
  *
  * The same file sits in `exercises` and in `solutions`, so a stub and its
  * answer program against identical definitions. The hierarchy is redeclared
  * here rather than imported from chapter 04, so this chapter can be studied
  * on its own. As in chapter 04, `Monad` derives `ap` and `map` from
  * `flatMap`.
  *
  * The `IO` of exercises 02 to 04 sits next to this file, in `IO.scala`.
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
  *
  * For `IO` the `==` above cannot be taken literally: a description holds
  * functions, and functions have no equality. Two `IO`s are equal when running
  * them gives the same result and performs the same effects, in the same
  * order.
  */
trait Monad[F[_]] extends Applicative[F]:
  extension [A](fa: F[A]) def flatMap[B](f: A => F[B]): F[B]

  extension [A](fa: F[A]) override def map[B](f: A => B): F[B] = fa.flatMap(a => pure(f(a)))

  extension [A, B](ff: F[A => B]) def ap(fa: F[A]): F[B] = ff.flatMap(f => fa.map(f))

object Monad:
  def apply[F[_]](using monad: Monad[F]): Monad[F] = monad
