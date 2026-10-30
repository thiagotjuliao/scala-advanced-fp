package advfp.typeclasses

/** A structure whose elements can be reduced to one value.
  *
  * `foldLeft(fa)(z)(f)` visits every element exactly once, left to right,
  * starting from `z`: for a list, `f(f(f(z, a1), a2), a3)`. The order matters
  * whenever `f` does not commute. It is strict, and an instance keeps it in
  * constant stack: the kernel folds tables longer than anything a generator
  * produces.
  *
  * `Foldable` has no laws of its own. What constrains it is agreement with
  * `Traverse`, once a type has both: folding is traversing in `Const`.
  *
  * `Foldable` does not extend `Functor`, so it is a second root of the
  * hierarchy (docs/kernel/decisions/0007-one-instance-per-type.md, addendum).
  */
trait Foldable[F[_]]:
  def foldLeft[A, B](fa: F[A])(z: B)(f: (B, A) => B): B

/** Aliases to the instances in `Functor`'s companion, not instances of their
  * own: a search for `Foldable` does not look there (ADR 0007, addendum).
  */
object Foldable:
  def apply[F[_]](using ev: Foldable[F]): Foldable[F] = ev

  given Foldable[Option] = Functor.optionInstances

  given Foldable[List] = Functor.listInstances
