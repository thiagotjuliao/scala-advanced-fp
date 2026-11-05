package advfp.typeclasses

/** An applicative whose next step can depend on the value of the previous one.
  *
  * `flatMap` must keep three laws, for all `a`, `m`, `f`, `g`:
  *
  *   - left identity: `flatMap(pure(a))(f) == f(a)`
  *   - right identity: `flatMap(m)(pure) == m`
  *   - associativity:
  *     `flatMap(flatMap(m)(f))(g) == flatMap(m)(a => flatMap(f(a))(g))`
  *
  * and `ap` must agree with it: `ap(ff)(fa) == flatMap(ff)(f => map(fa)(f))`.
  * The applicative laws cannot see that agreement: a `List` whose `ap` runs the
  * values as the outer loop, or an `Either` whose `ap` keeps the value's
  * `Left`, keeps them all and is still not this monad's applicative.
  *
  * `map` is derived from `flatMap` and `pure`. `ap` is left to the instance,
  * since deriving it from `flatMap` while `Applicative` derives `map` from `ap`
  * is a cycle.
  */
trait Monad[F[_]] extends Applicative[F]:
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  override def map[A, B](fa: F[A])(f: A => B): F[B] =
    flatMap(fa)(a => pure(f(a)))

/** The instances are in `Functor`'s companion, the root of the hierarchy
  * (docs/kernel/decisions/0007-one-instance-per-type.md).
  */
object Monad:
  def apply[F[_]](using ev: Monad[F]): Monad[F] = ev
