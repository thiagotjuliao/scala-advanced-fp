package advfp.typeclasses

/** A structure that can be walked with an effect, keeping its shape:
  * `traverse(fa)(f)` runs `f` on every element and gathers the effects into one
  * `G`, with `F`'s shape inside it.
  *
  * Effects run in the order `foldLeft` visits the elements, left to right: on
  * `Either`, the first `Left` is the result; on `List`, the first element is
  * the outer loop. `G` is only an `Applicative`, so the walk is fixed before it
  * starts, and `f` runs on every element even after an effect has failed.
  *
  * Laws, for all `fa`, `f`, `g`:
  *
  *   - identity: with `G = Id`, `traverse(fa)(f) == map(fa)(f)`
  *   - composition: traversing with `f` and then `g` is one traversal in the
  *     nested applicative `G[H[_]]`
  *   - naturality: for an applicative morphism `t: G ~> H`,
  *     `t(traverse(fa)(f)) == traverse(fa)(a => t(f(a)))`
  *
  * and `foldLeft` must agree with it: folding is traversing in `Const`.
  * `map` and `foldLeft` come from the instance, not from `traverse`, so these
  * agreements are what the tests check.
  */
trait Traverse[F[_]] extends Foldable[F], Functor[F]:
  def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]]

/** The instances are in `Functor`'s companion, with aliases in `Foldable`'s
  * (docs/kernel/decisions/0007-one-instance-per-type.md, addendum).
  */
object Traverse:
  def apply[F[_]](using ev: Traverse[F]): Traverse[F] = ev
