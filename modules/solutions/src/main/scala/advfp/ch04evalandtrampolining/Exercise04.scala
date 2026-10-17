package advfp.ch04evalandtrampolining

/** Exercise 04 — Make `State` stack-safe by running it on `Eval`. *Solved.*
  *
  * `get`, `set` and `modify` are chapter 03's, with the pair wrapped in
  * `Eval.now`: none of them runs another program, so none of them can grow
  * the stack.
  *
  * `flatMap` is `State(s => Eval.defer(fa.run(s)).flatMap((s1, a) =>
  * f(a).run(s1)))`, and both halves matter. The second program runs inside
  * `Eval.flatMap`, so the trampoline calls it — that is what makes a
  * right-nested chain safe. The `defer` makes a left-nested one safe. Without
  * it, running the outermost of a million left-nested programs calls
  * `fa.run(s)` at once, which is a `flatMap` too and calls its own `fa.run(s)`
  * at once, a million calls deep, before the first `Eval` is returned for the
  * trampoline to take over: the overflow of chapter 03, moved from `.value` to
  * `.run`. That version passes every test except the left-nested one. Calling
  * `.value` inside `flatMap` is worse: every step starts a trampoline of its
  * own on the JVM stack, and the right-nested chain and `forEach` overflow
  * too.
  *
  * `forEach` is `f` of the head followed by `forEach` of the tail, and it is
  * stack-safe without any effort of its own: the recursive call is inside the
  * function passed to `flatMap`, so it happens in the trampoline, one element
  * at a time. The same `forEach` on chapter 03's `State` overflows — at 64,241
  * elements, measured 2026-10-17 — and nothing in it changed but the monad
  * underneath.
  */
object Exercise04:

  final case class State[S, A](run: S => Eval[(S, A)])

  object State:
    def get[S]: State[S, S] = State(s => Eval.now((s, s)))

    def set[S](s: S): State[S, Unit] = State(_ => Eval.now((s, ())))

    def modify[S](f: S => S): State[S, Unit] = State(s => Eval.now((f(s), ())))

  given stateMonad: [S] => Monad[[A] =>> State[S, A]]:
    def pure[A](a: A): State[S, A] = State(s => Eval.now((s, a)))

    extension [A](fa: State[S, A])
      def flatMap[B](f: A => State[S, B]): State[S, B] =
        State(s => Eval.defer(fa.run(s)).flatMap((s1, a) => f(a).run(s1)))

  def forEach[S, A](as: List[A])(f: A => State[S, Unit]): State[S, Unit] =
    as match
      case Nil => stateMonad.pure(())
      case a :: rest => f(a).flatMap(_ => forEach(rest)(f))
end Exercise04
