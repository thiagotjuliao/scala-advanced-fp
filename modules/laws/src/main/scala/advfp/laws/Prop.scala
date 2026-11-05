package advfp.laws

/** A property: where its inputs come from, and what each one must satisfy.
  * Plain data: `check` runs it, shrinks a failing input, and catches what
  * `verdict` throws, which is a failure like any other.
  */
final case class Prop[A](gen: Gen[A], verdict: A => Verdict)

object Prop:
  /** `f` is not called here, and what it throws is not caught: both are the
    * runner's.
    */
  def forAll[A, R](gen: Gen[A])(f: A => R)(using Testable[R]): Prop[A] =
    Prop(gen, a => Testable[R].verdict(f(a)))
