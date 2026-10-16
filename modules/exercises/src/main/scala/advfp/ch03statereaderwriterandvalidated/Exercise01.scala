package advfp.ch03statereaderwriterandvalidated

import advfp.core.unsolved

/** Exercise 01 — Write `State`, and run a stack machine in it.
  *
  * A `State[S, A]` is a program that, given a state, returns the next state
  * and a value. It holds no state of its own.
  *
  *   - `get`, `set` and `modify`: read the state as the value, replace it, and
  *     transform it. They are the only operations that touch the state
  *     directly; everything else is built from them.
  *   - `stateMonad`: `pure` and `flatMap`. `ap` and `map` come with `Monad`.
  *     `fa.flatMap(f)` runs `fa`, then runs `f` of its value on the state `fa`
  *     *left behind* — not on the one it started from.
  *   - `step`: one instruction of a stack machine, as a change to the stack,
  *     top first. `Push(n)` puts `n` on top; `Add` and `Mul` replace the top
  *     two with their sum or product; `Dup` copies the top; `Swap` exchanges
  *     the top two; `Pop` drops the top. An instruction that finds too few
  *     numbers leaves the stack as it is.
  *   - `execute`: a whole program, the instructions in order.
  *
  * Hint: `execute` of an empty program is `pure(())`, and of `i :: rest` is
  * `step(i)` followed by `execute(rest)` — which operation is "followed by"?
  */
object Exercise01:

  final case class State[S, A](run: S => (S, A))

  object State:
    def get[S]: State[S, S] = unsolved

    def set[S](s: S): State[S, Unit] = unsolved

    def modify[S](f: S => S): State[S, Unit] = unsolved

  given stateMonad: [S] => Monad[[A] =>> State[S, A]] = unsolved

  enum Instr:
    case Push(n: Int)
    case Add, Mul, Dup, Swap, Pop

  def step(instr: Instr): State[List[Int], Unit] = unsolved

  def execute(program: List[Instr]): State[List[Int], Unit] = unsolved
end Exercise01
