package advfp.typeclasses

/** A functor that can lift a plain value, and apply functions that are
  * themselves inside the context.
  *
  * `ap(ff)(fa)` is `ff <*> fa`: functions first, as the laws are usually
  * written. For all `u`, `v`, `w`, `a`, `f`:
  *
  *   - identity: `ap(pure(a => a))(v) == v`
  *   - homomorphism: `ap(pure(f))(pure(a)) == pure(f(a))`
  *   - interchange: `ap(u)(pure(a)) == ap(pure(g => g(a)))(u)`
  *   - composition:
  *     `ap(ap(ap(pure(f => g => f compose g))(u))(v))(w) == ap(u)(ap(v)(w))`
  *
  * `map` is derived from `pure` and `ap`. An instance may override it with
  * something faster, as long as the two agree.
  */
trait Applicative[F[_]] extends Functor[F]:
  def pure[A](a: A): F[A]
  def ap[A, B](ff: F[A => B])(fa: F[A]): F[B]

  override def map[A, B](fa: F[A])(f: A => B): F[B] =
    ap(pure(f))(fa)

  /** `ff.ap(fa)`, found wherever an `Applicative[F]` given is visible. On a
    * type with a member `ap` of its own, the member wins.
    */
  extension [A, B](ff: F[A => B])
    def ap(fa: F[A])(using ev: Applicative[F]): F[B] =
      ev.ap(ff)(fa)

/** The instances are in `Functor`'s companion, the root of the hierarchy
  * (docs/kernel/decisions/0007-one-instance-per-type.md).
  */
object Applicative:
  def apply[F[_]](using ev: Applicative[F]): Applicative[F] = ev
