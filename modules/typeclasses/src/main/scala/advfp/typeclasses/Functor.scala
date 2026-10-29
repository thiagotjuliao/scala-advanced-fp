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

  given Functor[Option] = new Functor[Option]:
    override def map[A, B](fa: Option[A])(f: A => B): Option[B] =
      fa.map(f)

  /** Element by element, in order. */
  given Functor[List] = new Functor[List]:
    override def map[A, B](fa: List[A])(f: A => B): List[B] =
      fa.map(f)

  /** Maps the `Right`, and passes a `Left` through without calling `f`. The
    * types leave no other total implementation: there is no `E` to build a
    * `Left` from but the one given, and no `B` but `f`'s result.
    */
  given [E] => Functor[[A] =>> Either[E, A]] = new Functor[[A] =>> Either[E, A]]:
    override def map[A, B](fa: Either[E, A])(f: A => B): Either[E, B] =
      fa.map(f)
