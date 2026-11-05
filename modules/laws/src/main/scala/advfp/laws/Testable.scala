package advfp.laws

import Verdict.*
import advfp.typeclasses.Eq

/** A result a property may return: a `Law` or a `Boolean`. One `forAll` asks
  * for it, where two overloads were ambiguous on every untyped lambda
  * (docs/kernel/m01-the-law-checker.md#prop-the-runner-and-the-report). A
  * result with no instance, an `Int` by mistake, does not compile.
  */
trait Testable[R]:
  def verdict(r: R): Verdict

/** The instances are found without an import: `Testable` is part of the type
  * `forAll` asks for.
  */
object Testable:
  def apply[R](using ev: Testable[R]): Testable[R] = ev

  /** `false` fails with empty sides. */
  given Testable[Boolean] = r => if r then Holds else Fails("", "")

  /** Settles the sides' type here, so `Prop[A]` knows only its input: the
    * law is compared with `Eq[B]`, and the sides are kept as `toString`.
    */
  given [B] => (Eq[B]) => Testable[Law[B]] =
    case l @ Law(lhs, rhs) =>
      if l.holds then Holds else Fails(lhs.toString, rhs.toString)
