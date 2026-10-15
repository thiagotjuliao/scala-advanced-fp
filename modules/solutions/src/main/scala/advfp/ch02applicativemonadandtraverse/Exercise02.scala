package advfp.ch02applicativemonadandtraverse

/** Exercise 02 — Derive the combinators from the primitives. *Solved.*
  *
  * `map2` curries `f` and maps it over `fa`: that gives `F[B => C]`, a function
  * in the context waiting for its second argument, and `ap` supplies it from
  * `fb`. `fa` is mapped first and `fb` is applied second, so `fa`'s effect
  * runs first — the order the exercise asks for, and the one `Logged`
  * shows. Writing it as `fb.map(b => (a: A) => f(a, b)).ap(fa)` gives the same
  * value and the opposite log.
  *
  * `product` is `map2` with the pairing function, and `mapN` is the same
  * trick applied twice: map a function of three arguments, curried, then
  * `ap` twice. Each arity is one more `ap` — nothing else changes.
  *
  * `apFromFlatMap` takes the function out first and the value second, which is
  * the agreement law of exercise 01 read as a definition. Taking the value out
  * first, `fa.flatMap(a => ff.map(f => f(a)))`, has the same type and runs the
  * effects backwards; for a monad whose effects do not commute it is a
  * different, and wrong, `ap`.
  *
  * None of the four mentions a concrete type, so each one is written once and
  * holds for every applicative: that is what deriving it from the primitives
  * buys.
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
    def map2[B, C](fb: F[B])(f: (A, B) => C): F[C] =
      fa.map(a => (b: B) => f(a, b)).ap(fb)

    def product[B](fb: F[B]): F[(A, B)] = fa.map2(fb)((a, b) => (a, b))

  extension [F[_]: Applicative, A, B, C](fs: (F[A], F[B], F[C]))
    def mapN[D](f: (A, B, C) => D): F[D] =
      val (fa, fb, fc) = fs
      fa.map(a => (b: B) => (c: C) => f(a, b, c)).ap(fb).ap(fc)

  def apFromFlatMap[F[_]: Monad, A, B](ff: F[A => B])(fa: F[A]): F[B] =
    ff.flatMap(f => fa.map(f))
end Exercise02
