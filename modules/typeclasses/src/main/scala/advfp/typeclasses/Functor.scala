package advfp.typeclasses

/** A type constructor whose contents can be transformed without changing its
  * shape.
  *
  * `map` must keep two laws, for all `fa`, `f`, `g`:
  *
  *   - identity: `map(fa)(a => a) == fa`
  *   - composition: `map(map(fa)(f))(g) == map(fa)(f andThen g)`
  *
  * `map` is a method of the instance, not an extension: `Option`, `List` and
  * `Either` have a member `map`, which would win over an extension, so
  * `fa.map(f)` would never reach the instance (chapter 01, pitfalls). The
  * suites call `Functor[F].map(fa)(f)`.
  */
trait Functor[F[_]]:
  def map[A, B](fa: F[A])(f: A => B): F[B]

/** The instances for the standard types the suites are tested against. This
  * is the root of the hierarchy, so the instances of its subclasses will live
  * here too, one per type (docs/kernel/decisions/0007-one-instance-per-type.md):
  * when `Option` becomes a `Monad`, that instance replaces this one.
  */
object Functor:
  def apply[F[_]](using ev: Functor[F]): Functor[F] = ev

  /** `Some` only when both the function and the value are there. */
  given Monad[Option] = new Monad[Option]:
    override def pure[A](a: A): Option[A] = Some(a)

    override def ap[A, B](ff: Option[A => B])(fa: Option[A]): Option[B] =
      ff match
        case None => None
        case Some(f) => fa.map(f)

    override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] =
      fa.flatMap(f)

  /** Every function applied to every value, the functions as the outer loop:
    * `ap(List(f, g))(List(1, 2)) == List(f(1), f(2), g(1), g(2))`. Swapping
    * the loops gives another lawful applicative, but not the one that agrees
    * with `List`'s `flatMap`. Zipping instead is lawful only with an infinite
    * `pure` (chapter 02, exercise 04).
    */
  given Monad[List] = new Monad[List]:
    override def pure[A](a: A): List[A] = List(a)

    override def ap[A, B](ff: List[A => B])(fa: List[A]): List[B] =
      ff.flatMap(f => fa.map(f))

    override def flatMap[A, B](fa: List[A])(f: A => List[B]): List[B] =
      fa.flatMap(f)

  /** The function's side decides first: when both are `Left`, the function's
    * `Left` is the result, as agreeing with `flatMap` requires. `map` passes a
    * `Left` through without calling `f`.
    */
  given [E] => Monad[[A] =>> Either[E, A]] = new Monad[[A] =>> Either[E, A]]:
    override def pure[A](a: A): Either[E, A] = Right(a)

    override def ap[A, B](ff: Either[E, A => B])(fa: Either[E, A]): Either[E, B] =
      ff.flatMap(f => fa.map(f))

    override def flatMap[A, B](fa: Either[E, A])(f: A => Either[E, B]): Either[E, B] =
      fa.flatMap(f)

end Functor
