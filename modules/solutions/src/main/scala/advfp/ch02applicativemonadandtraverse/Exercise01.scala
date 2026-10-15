package advfp.ch02applicativemonadandtraverse

/** Exercise 01 — Write `Monad` for `Option`, `Either[E, *]` and `List`.
  * *Solved.*
  *
  * Each `flatMap` is the obvious one — the standard library's own, written out
  * by pattern matching. The work is in `ap`, which has a choice the types do not
  * make for you, and only one choice agrees with `flatMap`.
  *
  * For `Either`, `ff.flatMap(f => fa.map(f))` looks at `ff` first: if it is a
  * `Left`, that `Left` is the answer and `fa` is never looked at. So `ap` has
  * to return the function's `Left` when both are `Left`. Returning the value's
  * `Left` instead passes every law of `Applicative` and fails only the
  * agreement — and an `ap` that *combined* the two errors would be more useful
  * still, and lawless as a monad's `ap` for the same reason. That `ap` exists;
  * it is `Validated`, in chapter 03, and it gets no `flatMap`.
  *
  * For `List` the same argument fixes the order: `ff.flatMap(f => fa.map(f))`
  * takes the first function and applies it to every value, then the second.
  * The functions are the outer loop. Swapping the loops gives the same elements
  * in a different order — still a lawful applicative, and not this monad's.
  *
  * The instances are written with `given ...:` and an indented body. Overriding
  * `map` with the direct version would be faster than the inherited
  * `pure(f).ap(fa)`; it is left inherited, so the spec's `map` exercises `ap`.
  */
object Exercise01:

  given optionMonad: Monad[Option]:
    def pure[A](a: A): Option[A] = Some(a)

    extension [A, B](ff: Option[A => B])
      def ap(fa: Option[A]): Option[B] = (ff, fa) match
        case (Some(f), Some(a)) => Some(f(a))
        case _ => None

    extension [A](fa: Option[A])
      def flatMap[B](f: A => Option[B]): Option[B] = fa match
        case Some(a) => f(a)
        case None => None

  given eitherMonad: [E] => Monad[[A] =>> Either[E, A]]:
    def pure[A](a: A): Either[E, A] = Right(a)

    extension [A, B](ff: Either[E, A => B])
      def ap(fa: Either[E, A]): Either[E, B] = (ff, fa) match
        case (Right(f), Right(a)) => Right(f(a))
        case (Left(error), _) => Left(error)
        case (_, Left(error)) => Left(error)

    extension [A](fa: Either[E, A])
      def flatMap[B](f: A => Either[E, B]): Either[E, B] = fa match
        case Right(a) => f(a)
        case Left(error) => Left(error)

  given listMonad: Monad[List]:
    def pure[A](a: A): List[A] = List(a)

    extension [A, B](ff: List[A => B])
      def ap(fa: List[A]): List[B] =
        for
          f <- ff
          a <- fa
        yield f(a)

    extension [A](fa: List[A])
      def flatMap[B](f: A => List[B]): List[B] =
        fa.foldRight(List.empty[B])((a, acc) => f(a) ++ acc)
end Exercise01
