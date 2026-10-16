package advfp.ch02applicativemonadandtraverse

/** Exercise 01 — Write `Monad` for `Option`, `Either[E, *]` and `List`.
  *
  * Each instance writes `pure`, `ap` and `flatMap` — `Monad` here does not
  * derive `ap` for you. That is the point: three operations, and laws that tie
  * them together.
  *
  *   - `Option`: a missing value anywhere makes the result missing.
  *   - `Either[E, *]`: a `Left` anywhere makes the result that `Left`. When both
  *     sides of `ap` are `Left`s, the function's wins, because it comes first.
  *   - `List`: every function applied to every value, the functions on the
  *     outside — `List(f, g).ap(List(x, y))` is `List(f(x), f(y), g(x), g(y))`.
  *     `flatMap` concatenates.
  *
  * The spec checks the applicative laws, the monad laws, and the one that
  * binds them: `ff.ap(fa) == ff.flatMap(f => fa.map(f))`.
  *
  * Hint: write `flatMap` first, then ask what `ap` has to be so that it cannot
  * be told apart from `ff.flatMap(f => fa.map(f))`.
  */
object Exercise01:

  given optionMonad: Monad[Option] =
    new Monad[Option]:
      override def pure[A](a: A): Option[A] = Some(a)

      extension [A](fa: Option[A])
        override def flatMap[B](f: A => Option[B]): Option[B] =
          fa match
            case Some(a) => f(a)
            case None => None

      extension [A](fa: Option[A])
        override def map[B](f: A => B): Option[B] =
          fa.flatMap(a => pure(f(a)))

      extension [A, B](ff: Option[A => B])
        override def ap(fa: Option[A]): Option[B] =
          ff.flatMap(f => fa.map(a => f(a)))

  given eitherMonad: [E] => Monad[[A] =>> Either[E, A]] =
    new Monad[[A] =>> Either[E, A]]:
      override def pure[A](a: A): Either[E, A] = Right(a)

      extension [A](fa: Either[E, A])
        override def flatMap[B](f: A => Either[E, B]): Either[E, B] =
          fa match
            case Right(a) => f(a)
            case Left(e) => Left(e)

      extension [A](fa: Either[E, A])
        override def map[B](f: A => B): Either[E, B] =
          fa.flatMap(a => pure(f(a)))

      extension [A, B](ff: Either[E, A => B])
        override def ap(fa: Either[E, A]): Either[E, B] =
          ff.flatMap(f => fa.map(a => f(a)))

  given listMonad: Monad[List] =
    new Monad[List]:
      override def pure[A](a: A): List[A] = List(a)

      extension [A](fa: List[A])
        override def flatMap[B](f: A => List[B]): List[B] =
          fa match
            case h :: t => f(h) ++ t.flatMap(f)
            case Nil => Nil

      extension [A](fa: List[A])
        override def map[B](f: A => B): List[B] =
          fa.flatMap(a => pure(f(a)))

      extension [A, B](ff: List[A => B])
        override def ap(fa: List[A]): List[B] =
          ff.flatMap(f => fa.map(a => f(a)))
end Exercise01
