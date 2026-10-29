package advfp.typeclasses

/** A way to combine two values into one.
  *
  * `combine` must be associative, for all `a`, `b`, `c`:
  * `combine(combine(a, b), c) == combine(a, combine(b, c))`. The types cannot
  * enforce it. It is what lets a fold be regrouped — split in halves, run in
  * parallel, resumed from a partial result — without changing its answer.
  */
trait Semigroup[A]:
  def combine(x: A, y: A): A

/** The instances for the standard types the suites are tested against, for
  * `Semigroup` and `Monoid` both: one per type, here at the root of the
  * hierarchy, so a search for either finds it (see
  * docs/kernel/decisions/0007-one-instance-per-type.md). Each one built from
  * `Semigroup` of its elements is associative when that is.
  */
object Semigroup:
  def apply[A](using ev: Semigroup[A]): Semigroup[A] = ev

  extension [A](self: A)
    def |+|(other: A)(using ev: Semigroup[A]): A =
      ev.combine(self, other)

  /** Addition, with 0. It wraps on overflow, and stays associative: addition
    * modulo 2^32 is.
    */
  given Monoid[Int] = new Monoid[Int]:
    override def empty: Int = 0
    override def combine(x: Int, y: Int): Int = x + y

  /** `None` is the identity, so `Option[A]` is a monoid given nothing more
    * than `Semigroup[A]`: `Option` adds the identity that `A` may lack.
    */
  given [A] => (Semigroup[A]) => Monoid[Option[A]] = new Monoid[Option[A]]:
    override def empty: Option[A] = None
    override def combine(ox: Option[A], oy: Option[A]): Option[A] =
      (ox, oy) match
        case (ox, None) => ox
        case (None, oy) => oy
        case (Some(x), Some(y)) => Some(x |+| y)

  /** Concatenation, left operand first, with the empty list. It copies the
    * left operand, so a left-nested fold of many lists is quadratic.
    */
  given [A] => Monoid[List[A]] = new Monoid[List[A]]:
    override def empty: List[A] = List()
    override def combine(xs: List[A], ys: List[A]): List[A] = xs ++ ys

  /** The first `Left` wins, and errors are not combined, so `E` needs no
    * instance. This is the `map2` of `Either`'s applicative, which also keeps
    * the first `Left`. Accumulating errors is `Validated`'s job (chapter 03).
    *
    * Only a semigroup: `Right(Monoid[A].empty)` would be an identity, but it
    * needs `Monoid[A]`, and one instance cannot ask for both (ADR 0007).
    */
  given [E, A] => (Semigroup[A]) => Semigroup[Either[E, A]] =
    case (Left(e1), _) => Left(e1)
    case (_, Left(e2)) => Left(e2)
    case (Right(x), Right(y)) => Right(x |+| y)
end Semigroup
