package advfp.typeclasses

/** Equality as a decision, made per type — what the law suites compare with,
  * never `==`. See docs/kernel/decisions/0004-equality-as-a-typeclass.md.
  *
  * `eqv` must be an equivalence relation, for all `a`, `b`, `c`:
  *
  *   - reflexive: `eqv(a, a)`
  *   - symmetric: `eqv(a, b)` implies `eqv(b, a)`
  *   - transitive: `eqv(a, b)` and `eqv(b, c)` imply `eqv(a, c)`
  *
  * The types cannot enforce this, and the law checker assumes it: an `Eq` that
  * is not reflexive makes a lawful instance fail its laws, and one that is too
  * coarse lets an unlawful instance pass them.
  */
trait Eq[A]:
  def eqv(x: A, y: A): Boolean

/** The instances for the standard types the suites are tested against. Each
  * one built from `Eq` of its elements is an equivalence when those are.
  */
object Eq:
  def apply[A](using ev: Eq[A]): Eq[A] = ev

  extension [A](self: A)
    /** Not `eq`: that name is `AnyRef`'s reference equality, and as a member
      * it wins over an extension, so `xs eq ys` would compile and compare
      * references.
      */
    def ===(other: A)(using ev: Eq[A]): Boolean =
      ev.eqv(self, other)

  given Eq[Int] = _ == _

  given [A] => (Eq[A]) => Eq[Option[A]] =
    case (None, None) => true
    case (Some(x), Some(y)) => x === y
    case _ => false

  /** Element by element, in constant stack: the kernel compares tables with
    * it, and those are longer than anything a generator produces.
    */
  given [A] => (Eq[A]) => Eq[List[A]] = (xs, ys) =>
    @scala.annotation.tailrec
    def loop(xs: List[A], ys: List[A]): Boolean =
      (xs, ys) match
        case (Nil, Nil) => true
        case (x :: xt, y :: yt) => (x === y) && loop(xt, yt)
        case _ => false
    loop(xs, ys)

  given [E, A] => (Eq[A], Eq[E]) => Eq[Either[E, A]] =
    case (Left(e1), Left(e2)) => e1 === e2
    case (Right(x), Right(y)) => x === y
    case _ => false
end Eq
