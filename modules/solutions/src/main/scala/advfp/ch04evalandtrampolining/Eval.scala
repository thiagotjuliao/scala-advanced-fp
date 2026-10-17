package advfp.ch04evalandtrampolining

import scala.annotation.tailrec

/** A computation of an `A` that is stack-safe to compose: given, not an
  * exercise.
  *
  * Exercises 03 and 04 are about *using* a stack-safe `Eval`, and they must
  * run before exercise 02, which asks for one, is solved. So this is a
  * complete one, shared by both modules — and, in effect, an answer to
  * exercise 02. Solve that one first.
  *
  * Building an `Eval` runs nothing, except `now`, whose argument is a value
  * already. `.value` runs it, in a loop whose stack does not grow with the
  * number of `map`s, `flatMap`s or `defer`s.
  */
sealed abstract class Eval[+A]:
  def value: A = Eval.run(this)

  def flatMap[B](f: A => Eval[B]): Eval[B] = Eval.FlatMap(this, f)

  def map[B](f: A => B): Eval[B] = flatMap(a => Eval.Now(f(a)))

object Eval:

  /** A value computed already. */
  def now[A](a: A): Eval[A] = Now(a)

  /** Computed on the first `.value` and remembered: later calls do not
    * compute it again. Only this `Eval` remembers — one mapped from it
    * computes its own result anew on each `.value`.
    */
  def later[A](a: => A): Eval[A] = Later(() => a)

  /** Computed again on every `.value`. */
  def always[A](a: => A): Eval[A] = Always(() => a)

  /** An `Eval` that is built only when it is run. The way to write a recursive
    * function on `Eval`: the recursive call goes inside `defer`, so building
    * the result does not recurse.
    */
  def defer[A](eval: => Eval[A]): Eval[A] = Defer(() => eval)

  // The leaves: an `Eval` that produces its value without running another.
  private sealed abstract class Leaf[+A] extends Eval[A]:
    def get: A

  private final class Now[+A](a: A) extends Leaf[A]:
    def get: A = a

  // A class and not an enum case: an enum case cannot hold the `lazy val` that
  // does the remembering.
  private final class Later[+A](thunk: () => A) extends Leaf[A]:
    lazy val get: A = thunk()

  private final class Always[+A](thunk: () => A) extends Leaf[A]:
    def get: A = thunk()

  private final class Defer[+A](val thunk: () => Eval[A]) extends Eval[A]

  private final class FlatMap[X, +A](val source: Eval[X], val f: X => Eval[A]) extends Eval[A]

  // The trampoline. Each step either finishes or rewrites the `Eval` into one
  // closer to a leaf, and recurses in tail position. The one rule that keeps
  // the stack flat is the last: a `FlatMap` whose source is a `FlatMap` is
  // re-associated to the right instead of being run.
  @tailrec private def run[A](eval: Eval[A]): A =
    eval match
      case leaf: Leaf[A] => leaf.get
      case defer: Defer[A] => run(defer.thunk())
      case bind: FlatMap[x, A] => run(step(bind.source, bind.f))

  // One step of a `FlatMap`, apart from `run` so that the source's type has a
  // name, `X`, that `f` can be checked against.
  private def step[X, A](source: Eval[X], f: X => Eval[A]): Eval[A] =
    source match
      case leaf: Leaf[X] => f(leaf.get)
      case defer: Defer[X] => FlatMap(defer.thunk(), f)
      case inner: FlatMap[y, X] => FlatMap(inner.source, (y: y) => FlatMap(inner.f(y), f))

  given evalMonad: Monad[Eval]:
    def pure[A](a: A): Eval[A] = Eval.now(a)

    extension [A](fa: Eval[A]) def flatMap[B](f: A => Eval[B]): Eval[B] = fa.flatMap(f)
end Eval
