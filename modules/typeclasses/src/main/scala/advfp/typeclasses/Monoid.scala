package advfp.typeclasses

/** A semigroup with an identity: what a fold starts from, and what it returns
  * on nothing.
  *
  * `empty` must be neutral on both sides, for all `a`:
  * `combine(empty, a) == a` and `combine(a, empty) == a`. Associativity, from
  * `Semigroup`, says nothing about it: `*` with 0 as `empty` is associative
  * and has no identity.
  *
  * The instances are in `Semigroup`'s companion, not here, so that a search for
  * `Semigroup` finds them too (docs/kernel/decisions/0007-one-instance-per-type.md).
  */
trait Monoid[A] extends Semigroup[A]:
  def empty: A

object Monoid:
  def apply[A](using ev: Monoid[A]): Monoid[A] = ev
