package advfp.ch03statereaderwriterandvalidated

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
    def get[S]: State[S, S] = State: s =>
      (s, s)

    def set[S](s: S): State[S, Unit] = State: _ =>
      (s, ())

    def modify[S](f: S => S): State[S, Unit] = State: s =>
      (f(s), ())

  given stateMonad: [S] => Monad[[A] =>> State[S, A]] =
    new Monad[[A] =>> State[S, A]]:
      def pure[A](a: A): State[S, A] = State: s =>
        (s, a)

      extension [A](fa: State[S, A])
        override def flatMap[B](f: A => State[S, B]): State[S, B] =
          State: s =>
            val (s1, a) = fa.run(s)
            f(a).run(s1)

      extension [A](fa: State[S, A])
        override def map[B](f: A => B): State[S, B] =
          State: s =>
            val (s1, a) = fa.run(s)
            (s1, f(a))

      extension [A, B](ff: State[S, A => B])
        override def ap(fa: State[S, A]): State[S, B] =
          State: s =>
            val (s1, f) = ff.run(s)
            val (s2, a) = fa.run(s1)
            (s2, f(a))

  enum Instr:
    case Push(n: Int)
    case Add, Mul, Dup, Swap, Pop

  def step(instr: Instr): State[List[Int], Unit] = State: stack =>
    instr match
      case Instr.Push(n) => (n :: stack, ())
      case Instr.Add if stack.length >= 2 =>
        val x = stack.head
        val y = stack.tail.head
        val z = x + y
        (z :: stack.tail.tail, ())
      case Instr.Mul if stack.length >= 2 =>
        val x = stack.head
        val y = stack.tail.head
        val z = x * y
        (z :: stack.tail.tail, ())
      case Instr.Swap if stack.length >= 2 =>
        val x = stack.head
        val y = stack.tail.head
        (y :: x :: stack.tail.tail, ())
      case Instr.Dup if stack.length >= 1 =>
        val x = stack.head
        (x :: stack, ())
      case Instr.Pop if stack.length >= 1 =>
        (stack.tail, ())
      case _ => (stack, ())

  def execute(program: List[Instr]): State[List[Int], Unit] =
    State: s =>
      val res = program.foldLeft(s): (stack, instr) =>
        step(instr).run(stack)._1
      (res, ())
end Exercise01
