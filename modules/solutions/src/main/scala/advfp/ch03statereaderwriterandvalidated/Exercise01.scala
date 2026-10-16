package advfp.ch03statereaderwriterandvalidated

/** Exercise 01 — Write `State`, and run a stack machine in it. *Solved.*
  *
  * `get` returns the state as the value, `set` ignores the incoming state, and
  * `modify` is `set` of a function of it. Each one is a single function from
  * state to pair, and nothing else in the file touches a state by hand.
  *
  * `flatMap` is where the plumbing lives. It runs `fa` on `s0`, gets `(s1, a)`,
  * and runs `f(a)` on `s1`. Running it on `s0` instead has the same type and
  * the same value whenever `fa` leaves the state alone — `get`, `pure` — which
  * is why the spec's first `flatMap` test starts with `modify`: that version
  * returns `(0, 0)` where this one returns `(1, 1)`. It is the dice bug of the
  * theory document, made once and for all instead of at every call.
  *
  * `step` is one `modify` per instruction: each instruction is a function from
  * stack to stack, and the pattern matches say what happens to a short stack —
  * the last case of each leaves it as it is. `execute` is `step` of the head
  * followed, through `flatMap`, by `execute` of the rest; the order of the
  * list is the order of the effects, which the `Swap`/`Pop` test checks.
  *
  * `execute` is not stack-safe: each `flatMap` nests one more function call
  * when the program is run. Ten thousand `Push`es run, fifty thousand
  * overflow (measured 2026-10-16). Chapter 04 fixes `State`, not this
  * function.
  */
object Exercise01:

  final case class State[S, A](run: S => (S, A))

  object State:
    def get[S]: State[S, S] = State(s => (s, s))

    def set[S](s: S): State[S, Unit] = State(_ => (s, ()))

    def modify[S](f: S => S): State[S, Unit] = State(s => (f(s), ()))

  given stateMonad: [S] => Monad[[A] =>> State[S, A]]:
    def pure[A](a: A): State[S, A] = State(s => (s, a))

    extension [A](fa: State[S, A])
      def flatMap[B](f: A => State[S, B]): State[S, B] =
        State { s0 =>
          val (s1, a) = fa.run(s0)
          f(a).run(s1)
        }

  enum Instr:
    case Push(n: Int)
    case Add, Mul, Dup, Swap, Pop

  def step(instr: Instr): State[List[Int], Unit] =
    State.modify { stack =>
      (instr, stack) match
        case (Instr.Push(n), _) => n :: stack
        case (Instr.Add, a :: b :: rest) => (a + b) :: rest
        case (Instr.Mul, a :: b :: rest) => (a * b) :: rest
        case (Instr.Dup, a :: rest) => a :: a :: rest
        case (Instr.Swap, a :: b :: rest) => b :: a :: rest
        case (Instr.Pop, _ :: rest) => rest
        case _ => stack
    }

  def execute(program: List[Instr]): State[List[Int], Unit] =
    program match
      case Nil => stateMonad.pure(())
      case instr :: rest => step(instr).flatMap(_ => execute(rest))
end Exercise01
