package advfp.ch04evalandtrampolining

import advfp.core.unsolved

/** Exercise 04 — Make `State` stack-safe by running it on `Eval`.
  *
  * Chapter 03's `State[S, A]` was `S => (S, A)`, and a loop of ten thousand
  * `flatMap`s overflowed when it ran. Here the function returns an `Eval`:
  * `S => Eval[(S, A)]`. The `Eval` is the one in `Eval.scala`, given and
  * stack-safe.
  *
  *   - `get`, `set` and `modify`, as in chapter 03.
  *   - `stateMonad`: `pure` and `flatMap`. A million `flatMap`s, nested to the
  *     left as `foldLeft` builds them or to the right as recursion does, run
  *     on `.run(s).value` without overflowing.
  *   - `forEach`: run `f` on every element, in order, threading the state
  *     through — a million elements included.
  *
  * Hint: `Eval` keeps the stack flat only for the work it can see. If
  * `flatMap`'s `run` calls the first program's `run` directly, every one of a
  * million nested programs is called on the JVM stack before any `Eval` is
  * returned. What postpones that call?
  */
object Exercise04:

  final case class State[S, A](run: S => Eval[(S, A)])

  object State:
    def get[S]: State[S, S] = unsolved

    def set[S](s: S): State[S, Unit] = unsolved

    def modify[S](f: S => S): State[S, Unit] = unsolved

  given stateMonad: [S] => Monad[[A] =>> State[S, A]] = unsolved

  def forEach[S, A](as: List[A])(f: A => State[S, Unit]): State[S, Unit] = unsolved
end Exercise04
