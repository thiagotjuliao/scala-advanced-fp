package advfp.ch04evalandtrampolining

import scala.annotation.tailrec

/** Exercise 02 — Write `Eval`: strict, lazy and memoized values, stack-safe
  * to compose. *Solved.*
  *
  * Exercise 01's trampoline, with three kinds of leaf instead of one. `Now`,
  * `Later` and `Always` all produce a value without running another `Eval`, so
  * the loop treats them alike through `get`; they differ only in what `get`
  * is. `Now` holds a value. `Always` holds a thunk and calls it each time.
  * `Later` holds a thunk and a `lazy val` that calls it once — the only
  * mutable state in the chapter, and the compiler's, not ours. `Defer` is
  * `More`: one step to take before there is anything to look at. `map` and
  * `flatMap` build a `FlatMap` node and run nothing.
  *
  * The run loop is exercise 01's, with the same rule for the case that
  * matters: a `FlatMap` whose source is a `FlatMap` is re-associated, never
  * run. `step` is split from `run` only for the types: inside it, the source's
  * value type has a name, `X`, against which `f` can be checked.
  *
  * Two near misses compile and pass the small tests. A `map` written as
  * `Now(f(value))` computes at once, and runs `f` before anyone asked: the
  * laziness tests catch it, and a `later` mapped that way is no longer lazy at
  * all. A `later` written as `Always` computes twice, which is correct in value
  * and wrong in cost; only a test that counts calls sees it.
  *
  * A `later` keeps its thunk after computing, and with it everything the thunk
  * captured. `cats.Eval` clears it, with a `var`; here the leak is accepted to
  * keep the type immutable.
  */
object Exercise02:

  sealed abstract class Eval[+A]:
    def value: A = Eval.run(this)

    def flatMap[B](f: A => Eval[B]): Eval[B] = Eval.FlatMap(this, f)

    def map[B](f: A => B): Eval[B] = flatMap(a => Eval.Now(f(a)))

  object Eval:
    def now[A](a: A): Eval[A] = Now(a)

    def later[A](a: => A): Eval[A] = Later(() => a)

    def always[A](a: => A): Eval[A] = Always(() => a)

    def defer[A](eval: => Eval[A]): Eval[A] = Defer(() => eval)

    private sealed abstract class Leaf[+A] extends Eval[A]:
      def get: A

    private final class Now[+A](a: A) extends Leaf[A]:
      def get: A = a

    private final class Later[+A](thunk: () => A) extends Leaf[A]:
      lazy val get: A = thunk()

    private final class Always[+A](thunk: () => A) extends Leaf[A]:
      def get: A = thunk()

    private final class Defer[+A](val thunk: () => Eval[A]) extends Eval[A]

    private final class FlatMap[X, +A](val source: Eval[X], val f: X => Eval[A]) extends Eval[A]

    @tailrec private def run[A](eval: Eval[A]): A =
      eval match
        case leaf: Leaf[A] => leaf.get
        case defer: Defer[A] => run(defer.thunk())
        case bind: FlatMap[x, A] => run(step(bind.source, bind.f))

    private def step[X, A](source: Eval[X], f: X => Eval[A]): Eval[A] =
      source match
        case leaf: Leaf[X] => f(leaf.get)
        case defer: Defer[X] => FlatMap(defer.thunk(), f)
        case inner: FlatMap[y, X] => FlatMap(inner.source, (y: y) => FlatMap(inner.f(y), f))
  end Eval

  given evalMonad: Monad[Eval]:
    def pure[A](a: A): Eval[A] = Eval.now(a)

    extension [A](fa: Eval[A]) def flatMap[B](f: A => Eval[B]): Eval[B] = fa.flatMap(f)
end Exercise02
