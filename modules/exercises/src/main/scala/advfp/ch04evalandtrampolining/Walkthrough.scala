package advfp.ch04evalandtrampolining

import scala.annotation.tailrec
import scala.util.control.TailCalls
import scala.util.control.TailCalls.TailRec

/** Chapter 04 — Eval and trampolining.
  *
  * Read this file top to bottom before opening the exercises. It is the whole
  * chapter in executable form: it compiles with the module, so none of it is
  * pseudo-code.
  *
  * Prose version: `docs/theory/ch04-eval-and-trampolining.md`.
  */
object Walkthrough:

  // --- 1. Every call is a stack frame ------------------------------------------
  //
  // A recursive function spends one frame per call, and the JVM gives a thread
  // a stack of fixed size. These two are correct and overflow on a large
  // enough `n`.

  def isEvenPlain(n: Int): Boolean = if n == 0 then true else isOddPlain(n - 1)

  def isOddPlain(n: Int): Boolean = if n == 0 then false else isEvenPlain(n - 1)

  // --- 2. @tailrec fixes self-calls only ---------------------------------------
  //
  // A call to itself in tail position is compiled into a jump, and `@tailrec`
  // makes the compiler say so or refuse. This runs at any `n`.

  @tailrec def isEvenLoop(n: Int, even: Boolean = true): Boolean =
    if n == 0 then even else isEvenLoop(n - 1, !even)

  // The mutual version cannot be annotated: the call to `isOddPlain` is in tail
  // position, but it is a call to another method, and the JVM has no tail
  // calls. Merging the two methods into one, as above, works here and does not
  // scale — a parser with forty mutually recursive rules is not one method.

  // --- 3. Return the call instead of making it ---------------------------------
  //
  // The standard library has a small trampoline, `TailCalls`. `tailcall(...)`
  // does not call: it returns a value saying "call this next", and `.result`
  // is a loop that keeps taking steps until one is `done`.

  def isEvenT(n: Int): TailRec[Boolean] =
    if n == 0 then TailCalls.done(true) else TailCalls.tailcall(isOddT(n - 1))

  def isOddT(n: Int): TailRec[Boolean] =
    if n == 0 then TailCalls.done(false) else TailCalls.tailcall(isEvenT(n - 1))

  // `isEvenT(1_000_000).result` is `true`, and the stack never grew: building
  // `isEvenT(1_000_000)` returns at once, and the million steps happen inside
  // `.result`, one at a time. Exercise 01 builds this type, and the `flatMap`
  // that `TailCalls` also has.

  // --- 4. When is a value computed? --------------------------------------------
  //
  // Scala has three answers built in, and `Eval` makes each of them a value
  // that can be passed around and composed.

  final class Strategies:
    val now: Int = 1 + 1 // once, right away        — Eval.now
    lazy val later: Int = 1 + 1 // once, on first use       — Eval.later
    def always: Int = 1 + 1 // on every use             — Eval.always

  // The difference matters when computing is expensive, or has an effect, and
  // the value may be needed zero times, once, or many. Exercise 02 builds
  // `Eval` with all three, on exercise 01's trampoline.

  // --- 5. A right fold that cannot stop ----------------------------------------
  //
  // The standard library's `foldRight` hands `f` the rest of the fold as a
  // finished value. To have that value, it has folded the whole tail first —
  // so `p` is called on every element, even after a match.

  def existsStrict[A](as: List[A])(p: A => Boolean): Boolean =
    as.foldRight(false)((a, rest) => p(a) || rest)

  // `||` would stop, but `rest` is already computed by the time it is looked
  // at. Handing `f` an `Eval[B]` instead of a `B` lets `f` decide whether to
  // run it. Exercise 03 writes that fold, on the `Eval` in `Eval.scala`.

  // --- 6. Chapter 03's State, and why it overflowed ----------------------------

  final case class PlainState[S, A](run: S => (S, A)):
    def flatMap[B](f: A => PlainState[S, B]): PlainState[S, B] =
      PlainState { s0 =>
        val (s1, a) = run(s0)
        f(a).run(s1)
      }

  def tick: PlainState[Int, Unit] = PlainState(n => (n + 1, ()))

  // A loop of `n` ticks, built with `foldLeft`: the outermost `run` calls the
  // next one in, which calls the next, `n` deep. Building it is fine; running
  // it overflows somewhere past a hundred thousand ticks (172,121 measured
  // 2026-10-17, warm JIT, sbt's 4 MB stack), and a cold JVM gives up sooner.
  def ticks(n: Int): PlainState[Int, Unit] =
    (1 to n).foldLeft(tick)((acc, _) => acc.flatMap(_ => tick))

  // The fix changes the result type, not the program: `S => Eval[(S, A)]`.
  // The nested calls become `Eval` nodes, and the trampoline runs them.
  // Exercise 04 makes that change, and finds the one place it needs `defer`.
end Walkthrough
