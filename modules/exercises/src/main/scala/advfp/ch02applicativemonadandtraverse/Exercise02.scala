package advfp.ch02applicativemonadandtraverse

import advfp.core.unsolved

/** Exercise 02 — Derive the combinators from the primitives.
  *
  * `Applicative` only has `pure` and `ap`. Everything else people call
  * "applicative" is built from those two, for every applicative at once:
  *
  *   - `map2`: combine two values in the context with a function of two
  *     arguments;
  *   - `product`: pair them up;
  *   - `mapN`, on a triple of values in the context: `map2` one arity up. The
  *     same construction goes to any arity, which is why the name has an `N`.
  *
  * And the other way round: every `Monad` determines an `ap`. Write
  * `apFromFlatMap` using `flatMap` and `map` only — not the instance's own
  * `ap`.
  *
  * All four must run the effects left to right: the effect of the first
  * argument before the second. `Logged` below records its effects, so the spec
  * can see the order.
  *
  * Hint: `ap` takes a function of *one* argument. A function of two arguments,
  * curried, is a function of one argument that returns another.
  */
object Exercise02:

  /** A value and the log of how it was produced. Given, not yours: a monad
    * whose effect is visible, so that the order effects run in can be read
    * off the log.
    */
  final case class Logged[A](log: List[String], value: A)

  object Logged:
    def say[A](message: String, value: A): Logged[A] = Logged(List(message), value)

  given loggedMonad: Monad[Logged]:
    def pure[A](a: A): Logged[A] = Logged(Nil, a)

    extension [A, B](ff: Logged[A => B])
      def ap(fa: Logged[A]): Logged[B] = Logged(ff.log ++ fa.log, ff.value(fa.value))

    extension [A](fa: Logged[A])
      def flatMap[B](f: A => Logged[B]): Logged[B] =
        val next = f(fa.value)
        Logged(fa.log ++ next.log, next.value)

  extension [F[_]: Applicative, A](fa: F[A])
    def map2[B, C](fb: F[B])(f: (A, B) => C): F[C] = unsolved

    def product[B](fb: F[B]): F[(A, B)] = unsolved

  extension [F[_]: Applicative, A, B, C](fs: (F[A], F[B], F[C]))
    def mapN[D](f: (A, B, C) => D): F[D] = unsolved

  def apFromFlatMap[F[_]: Monad, A, B](ff: F[A => B])(fa: F[A]): F[B] = unsolved
end Exercise02
