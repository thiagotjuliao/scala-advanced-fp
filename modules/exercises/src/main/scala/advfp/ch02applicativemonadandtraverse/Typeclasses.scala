package advfp.ch02applicativemonadandtraverse

/** The chapter's typeclasses: given, not exercises.
  *
  * The same file sits in `exercises` and in `solutions`, so a stub and its
  * answer program against identical definitions. The hierarchy is redeclared
  * here rather than imported from chapter 01, so this chapter can be studied
  * on its own.
  *
  * Each typeclass carries only its primitive operations. Everything that can be
  * derived from them — `map2`, `product`, `ap` from `flatMap`, `traverse` for a
  * list — is left out on purpose: deriving it is what the exercises are for.
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
  * `map` comes for free: `pure(f).ap(fa)`. An instance may override it with
  * something faster, as long as the two agree.
  *
  * Laws:
  *   - identity: `pure(identity).ap(v) == v`;
  *   - homomorphism: `pure(f).ap(pure(x)) == pure(f(x))`;
  *   - interchange: `u.ap(pure(y)) == pure((f: A => B) => f(y)).ap(u)`;
  *   - composition: `pure(compose).ap(u).ap(v).ap(w) == u.ap(v.ap(w))`, where
  *     `compose` is `(f: B => C) => (g: A => B) => f compose g`.
  */
trait Applicative[F[_]] extends Functor[F]:
  def pure[A](a: A): F[A]

  extension [A, B](ff: F[A => B]) def ap(fa: F[A]): F[B]

  extension [A](fa: F[A]) override def map[B](f: A => B): F[B] = pure(f).ap(fa)

object Applicative:
  def apply[F[_]](using applicative: Applicative[F]): Applicative[F] = applicative

  /** The applicative of no context at all. It lives in this companion so that
    * `Applicative[Id]` is found without an import, while its extension methods,
    * which accept any value, never leak into a call site that did not ask.
    */
  given idApplicative: Applicative[Id]:
    def pure[A](a: A): Id[A] = a
    extension [A, B](ff: Id[A => B]) def ap(fa: Id[A]): Id[B] = ff(fa)

/** A value with no context around it: `Id[Int]` is `Int`. */
type Id[A] = A

/** An applicative whose next step can depend on the value the previous one
  * produced.
  *
  * `ap` is not derived from `flatMap` here, so every instance writes both — and
  * the two must agree: `ff.ap(fa) == ff.flatMap(f => fa.map(f))`. An instance
  * whose `ap` does something its `flatMap` cannot reproduce is lawless, however
  * useful that `ap` looks.
  *
  * Laws, besides the applicative ones and that agreement:
  *   - left identity: `pure(a).flatMap(f) == f(a)`;
  *   - right identity: `fa.flatMap(pure) == fa`;
  *   - associativity: `fa.flatMap(f).flatMap(g) == fa.flatMap(a => f(a).flatMap(g))`.
  */
trait Monad[F[_]] extends Applicative[F]:
  extension [A](fa: F[A]) def flatMap[B](f: A => F[B]): F[B]

object Monad:
  def apply[F[_]](using monad: Monad[F]): Monad[F] = monad

/** A structure that can be collapsed, element by element, from the right.
  *
  * `foldRight` visits the elements in the structure's own order: for a list,
  * front to back. `toList` is that order made visible.
  */
trait Foldable[F[_]]:
  extension [A](fa: F[A])
    def foldRight[B](z: B)(f: (A, B) => B): B

    def toList: List[A] = fa.foldRight(List.empty[A])(_ :: _)

object Foldable:
  def apply[F[_]](using foldable: Foldable[F]): Foldable[F] = foldable

/** A structure that can be walked with an effect at every element, collecting
  * the effects in the structure's order and rebuilding the structure inside
  * them.
  *
  * `map` comes for free: it is `traverse` with no effect at all, `Id`.
  *
  * Laws — identity: `fa.traverse[Id, A](a => a) == fa`; and the effects run in
  * the order `foldRight` visits the elements.
  */
trait Traverse[F[_]] extends Functor[F], Foldable[F]:
  extension [A](fa: F[A]) def traverse[G[_]: Applicative, B](f: A => G[B]): G[F[B]]

  extension [A](fa: F[A]) override def map[B](f: A => B): F[B] = fa.traverse[Id, B](f)

  extension [G[_]: Applicative, A](fga: F[G[A]])
    def sequence: G[F[A]] = fga.traverse[G, A](ga => ga)

object Traverse:
  def apply[F[_]](using traverse: Traverse[F]): Traverse[F] = traverse
