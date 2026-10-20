package advfp.ch04evalandtrampolining

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
    def get[S]: State[S, S] = State(s => Eval.now((s, s)))

    def set[S](s: S): State[S, Unit] = State(_ => Eval.now((s, ())))

    def modify[S](f: S => S): State[S, Unit] = State(s => Eval.now(f(s), ()))

  given stateMonad: [S] => Monad[[A] =>> State[S, A]] =
    new Monad[[A] =>> State[S, A]]:
      override def pure[A](a: A): State[S, A] = State(s => Eval.now(s, a))

      extension [A](fa: State[S, A])
        override def flatMap[B](f: A => State[S, B]): State[S, B] =
          State(s => Eval.defer(fa.run(s)).flatMap((s, a) => f(a).run(s)))

  def forEach[S, A](as: List[A])(f: A => State[S, Unit]): State[S, Unit] =
    // State: s =>
    //   as.foldLeft(Eval.now((s, ()))): (acc, a) =>
    //     acc.flatMap: (s, _) =>
    //       f(a).run(s)
    as match
      case Nil => stateMonad.pure(())
      case h :: t => f(h).flatMap(_ => forEach(t)(f))

end Exercise04
