package advfp.ch04evalandtrampolining

/** Exercise 02 — Write `Eval`: strict, lazy and memoized values, stack-safe
  * to compose.
  *
  * An `Eval[A]` describes how to compute an `A`, and `.value` computes it.
  * The four ways to make one say *when*:
  *
  *   - `now(a)`: `a` is computed already, like a `val`;
  *   - `later(a)`: computed on the first `.value` and remembered, like a
  *     `lazy val` — a second `.value` does not compute it again;
  *   - `always(a)`: computed on every `.value`, like a `def`;
  *   - `defer(fa)`: `fa` itself is built only on `.value`.
  *
  * `map` and `flatMap` compute nothing either: they describe the next step.
  * And `.value` runs a million nested `map`s, `flatMap`s or `defer`s, nested
  * to the left or to the right, without overflowing.
  *
  * The representation is yours: replace the bodies below, and add the
  * subclasses of `Eval` you need in this file. `evalMonad` is `pure` and
  * `flatMap`, in terms of the methods above.
  *
  * Hint: this is exercise 01's trampoline with more kinds of leaf. Which of
  * the four constructors are leaves, and which one is a `More`? Remembering a
  * `later` needs one `lazy val`.
  */
object Exercise02:

  sealed abstract class Eval[+A]:
    final def value: A = Eval.run(this)

    def flatMap[B](f: A => Eval[B]): Eval[B] = FlatMap(this, f)

    def map[B](f: A => B): Eval[B] =
      val g: A => Eval[B] = a => Eval.now(f(a))
      this.flatMap(g)

  sealed abstract class Leaf[+A] extends Eval[A]:
    def get: A

  final case class Now[+A](override val get: A) extends Leaf[A]

  final case class Later[+A](thunk: () => A) extends Leaf[A]:
    override lazy val get: A = thunk()

  final case class Always[+A](thunk: () => A) extends Leaf[A]:
    override def get: A = thunk()

  final case class Defer[+A](eval: () => Eval[A]) extends Eval[A]

  final case class FlatMap[X, +A](source: Eval[X], f: X => Eval[A]) extends Eval[A]

  object Eval:
    def now[A](a: A): Eval[A] = Now(a)

    def later[A](a: => A): Eval[A] = Later(() => a)

    def always[A](a: => A): Eval[A] = Always(() => a)

    def defer[A](eval: => Eval[A]): Eval[A] = Defer(() => eval)

    private def step[X, A](source: Eval[X], f: X => Eval[A]): Eval[A] =
      source match
        case l: Leaf[X] => f(l.get)
        case d: Defer[?] => FlatMap(d.eval(), f)
        case FlatMap(source2, g) =>
          FlatMap(source2, x2 => FlatMap(g(x2), f))

    @annotation.tailrec
    private def run[A](eval: Eval[A]): A = eval match
      case l: Leaf[A] => l.get
      case d: Defer[?] => run(d.eval())
      case FlatMap(source, f) => run(step(source, f))

  given evalMonad: Monad[Eval] =
    new Monad[Eval]:
      override def pure[A](a: A): Eval[A] = Now(a)

      extension [A](fa: Eval[A])
        override def flatMap[B](f: A => Eval[B]): Eval[B] =
          FlatMap(fa, f)
end Exercise02
