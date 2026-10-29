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

/** The instances for the standard types the suites are tested against. Each
  * one built from `Semigroup` of its elements is associative when that is.
  */
object Semigroup:
  def apply[A](using ev: Semigroup[A]): Semigroup[A] = ev

  extension [A](self: A)
    def |+|(other: A)(using ev: Semigroup[A]): A =
      ev.combine(self, other)

  /** Addition. It wraps on overflow, and stays associative: addition modulo
    * 2^32 is.
    */
  given Semigroup[Int] = _ + _

  /** `None` is neutral on both sides, so this semigroup becomes a monoid with
    * `None` as its identity, given nothing more than `Semigroup[A]`.
    */
  given [A] => (Semigroup[A]) => Semigroup[Option[A]] =
    case (ox, None) => ox
    case (None, oy) => oy
    case (Some(x), Some(y)) => Some(x |+| y)

  /** Concatenation, left operand first. It copies the left operand, so a
    * left-nested fold of many lists is quadratic.
    */
  given [A] => Semigroup[List[A]] = _ ++ _

  /** The first `Left` wins, and errors are not combined, so `E` needs no
    * instance. This is the `map2` of `Either`'s applicative, which also keeps
    * the first `Left`. Accumulating errors is `Validated`'s job (chapter 03).
    */
  given [E, A] => (Semigroup[A]) => Semigroup[Either[E, A]] =
    case (Left(e1), _) => Left(e1)
    case (_, Left(e2)) => Left(e2)
    case (Right(x), Right(y)) => Right(x |+| y)
end Semigroup
