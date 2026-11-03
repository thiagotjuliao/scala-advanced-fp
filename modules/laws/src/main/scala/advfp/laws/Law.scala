package advfp.laws

import advfp.typeclasses.Eq
import advfp.typeclasses.Eq.===

/** A law as an equation, not a `Boolean`: when it fails, the report shows
  * both sides, which say what went wrong where "associativity failed" says
  * only that something did (docs/kernel/m01-the-law-checker.md#law).
  */
final case class Law[A](lhs: A, rhs: A):
  /** Compares the sides with `Eq`, never `==`, so the law means what the
    * type's equality means. An `Eq` that is not an equivalence makes `holds`
    * meaningless.
    */
  def holds(using Eq[A]): Boolean = lhs === rhs

/** `lhs <-> rhs` is `Law(lhs, rhs)`, so a suite reads as the equation it
  * checks. Top-level, so the suites see it without an import.
  *
  * Its precedence is that of `<`, above `|`: a side written with `|+|` needs
  * parentheses. `(a |+| b) |+| c <-> a |+| (b |+| c)` reads as
  * `(a |+| b) |+| (c <-> a) |+| (b |+| c)`, and fails with
  * `Found: advfp.laws.Law[Int]`, `Required: Int`.
  */
extension [A](lhs: A) def <->(rhs: A): Law[A] = Law(lhs, rhs)
